package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.component.DefaultColumnSeeder;
import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.dto.response.CalendarViewResponse;
import com.zone.tasksphere.dto.response.SubTaskResponse;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.mapper.TaskMapper;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.service.ReportService;
import com.zone.tasksphere.service.TaskService;
import com.zone.tasksphere.specification.TaskSpecification;
import com.zone.tasksphere.utils.TaskCodeGenerator;
import com.zone.tasksphere.repository.TaskDependencyRepository;
import com.zone.tasksphere.repository.ChecklistItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectStatusColumnRepository columnRepository;
    private final SprintRepository sprintRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final TaskCodeGenerator taskCodeGenerator;
    private final TaskMapper taskMapper;
    private final DefaultColumnSeeder defaultColumnSeeder;
    private final TaskDependencyRepository dependencyRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final com.zone.tasksphere.service.WebSocketService webSocketService;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    // ════════════════════════════════════════
    // P3-BE-01: CREATE TASK
    // ════════════════════════════════════════
    @Override
    public TaskDetailResponse createTask(UUID projectId, CreateTaskRequest request, UUID currentUserId) {
        Project project = getProject(projectId);
        User currentUser = getUser(currentUserId);

        // Validate membership & quyền tạo task
        ProjectMember member = getMember(projectId, currentUserId);
        if (member.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không được tạo task");
        }

        // Validate assignee là member của project
        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = getUser(request.getAssigneeId());
            if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getAssigneeId())) {
                throw new BadRequestException("Assignee không phải thành viên dự án");
            }
        }

        // Validate sprint thuộc project + chưa bị xóa
        Sprint sprint = null;
        if (request.getSprintId() != null) {
            sprint = sprintRepository
                .findByIdAndProject_IdAndDeletedAtIsNull(request.getSprintId(), projectId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại hoặc không thuộc dự án này"));
            if (sprint.getStatus() == SprintStatus.COMPLETED) {
                throw new BusinessRuleException("Không thể thêm task vào sprint đã hoàn thành");
            }
        }

        // Validate sub-task depth (BR-15: max depth = 3)
        Task parentTask = null;
        int depth = 0;
        if (request.getParentTaskId() != null) {
            parentTask = taskRepository.findById(request.getParentTaskId())
                .orElseThrow(() -> new NotFoundException("Parent task not found"));
            depth = parentTask.getDepth() + 1;
            if (depth > 3) {
                throw new BadRequestException("TSK_003: Sub-task depth limit exceeded (max 3 levels)");
            }
        }

        // Lấy status column (default = cột đầu tiên)
        ProjectStatusColumn statusColumn;
        if (request.getStatusColumnId() != null) {
            statusColumn = columnRepository.findById(request.getStatusColumnId())
                .orElseThrow(() -> new NotFoundException("Column not found"));
        } else {
            // Tầng 3 safety guard: nếu project chưa có column → tự seed rồi lấy cột đầu tiên
            statusColumn = getOrCreateDefaultColumn(project);
        }

        // Tính position (cuối cột)
        int position = (int) taskRepository.countByStatusColumnId(statusColumn.getId());

        // Sinh task code (thread-safe)
        String taskCode = taskCodeGenerator.generateTaskCode(project);

        // Build và save entity
        Task task = Task.builder()
            .taskCode(taskCode)
            .title(request.getTitle())
            .description(request.getDescription())
            .type(request.getType() != null ? request.getType() : TaskType.TASK)
            .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
            .taskStatus(statusColumn.getMappedStatus() != null ? statusColumn.getMappedStatus() : TaskStatus.TODO)
            .storyPoints(request.getStoryPoints())
            .estimatedHours(request.getEstimatedHours())
            .dueDate(request.getDueDate())
            .taskPosition(position)
            .depth(depth)
            .project(project)
            .assignee(assignee)
            .reporter(currentUser)
            .sprint(sprint)
            .statusColumn(statusColumn)
            .parentTask(parentTask)
            .build();

        task = taskRepository.save(task);

        // Ghi activity log
        logActivity(project.getId(), currentUserId, EntityType.TASK, task.getId(),
            ActionType.TASK_CREATED, null, toJson(Map.of(
                    "taskCode", taskCode,
                    "title", task.getTitle(),
                    "type", task.getType() != null ? task.getType().name() : null,
                    "priority", task.getPriority() != null ? task.getPriority().name() : null
            )));

        // Gửi notification nếu có assignee khác reporter
        if (assignee != null && !assignee.getId().equals(currentUserId)) {
            notificationService.createNotification(
                assignee, NotificationType.TASK_ASSIGNED,
                "Task mới được giao",
                String.format("Bạn được giao task [%s] %s", taskCode, task.getTitle()),
                EntityType.TASK.name(), task.getId()
            );
        }

        log.info("Task created: {} in project {}", taskCode, projectId);
        reportService.invalidateOverviewCache(projectId);
        return taskMapper.toDetailResponse(task);
    }

    // ════════════════════════════════════════
    // P3-BE-02: GET TASK LIST
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getTasks(UUID projectId, TaskFilterParams params,
                                               Pageable pageable, UUID currentUserId) {
        validateMembership(projectId, currentUserId);

        // Resolve "me" → currentUserId
        if ("me".equals(params.getAssigneeId())) {
            params.setAssigneeId(currentUserId.toString());
        }

        params.setProjectId(projectId);
        Specification<Task> spec = TaskSpecification.buildFilter(params);
        Page<Task> page = taskRepository.findAll(spec, pageable);

        return PageResponse.fromPage(page.map(taskMapper::toResponse));
    }

    // ════════════════════════════════════════
    // P3-BE-02: GET TASK DETAIL
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskById(UUID projectId, UUID taskId, UUID currentUserId) {
        validateMembership(projectId, currentUserId);
        Task task = getTaskInProject(taskId, projectId);

        TaskDetailResponse response = taskMapper.toDetailResponse(task);

        // Compute permission flags based on current user's role
        User currentUser = getUser(currentUserId);
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUserId);
        boolean isPM = isMemberPM(projectId, currentUserId);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;

        response.setCanEdit(isAssignee || isPM || isAdmin);
        response.setCanDelete(isPM || isAdmin);

        // Checklist counts (always from repository — not lazy loaded)
        response.setChecklistTotal((int) checklistItemRepository.countByTaskIdAndDeletedAtIsNull(taskId));
        response.setChecklistDone((int) checklistItemRepository.countByTaskIdAndIsCompletedTrueAndDeletedAtIsNull(taskId));

        // Build link summaries from this task's perspective
        response.setLinks(buildLinkSummaries(taskId));

        return response;
    }

    private List<TaskDetailResponse.TaskLinkSummary> buildLinkSummaries(UUID taskId) {
        return dependencyRepository.findLinksBySourceTaskId(taskId).stream()
            .map(dep -> {
                Task target = dep.getBlockedTask();
                return TaskDetailResponse.TaskLinkSummary.builder()
                    .id(dep.getId())
                    .linkType(dep.getLinkType().name())
                    .targetTask(TaskDetailResponse.TaskLinkSummary.TaskRef.builder()
                        .id(target.getId())
                        .taskId(target.getTaskCode())
                        .title(target.getTitle())
                        .status(target.getTaskStatus())
                        .build())
                    .build();
            })
            .toList();
    }

    // ════════════════════════════════════════
    // P3-BE-03: UPDATE TASK FULL (PUT)
    // ════════════════════════════════════════
    @Override
    public TaskDetailResponse updateTask(UUID projectId, UUID taskId,
                                         UpdateTaskRequest request, UUID currentUserId) {
        Task task = getTaskInProject(taskId, projectId);
        User currentUser = getUser(currentUserId);
        ProjectMember actorMember = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .orElse(null);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;
        if (actorMember == null && !isAdmin) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
        if (actorMember != null && actorMember.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không được sửa task");
        }
        UUID oldAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;
        String oldAssigneeName = task.getAssignee() != null ? task.getAssignee().getFullName() : null;
        String oldPriority = task.getPriority() != null ? task.getPriority().name() : null;
        UUID oldSprintId = task.getSprint() != null ? task.getSprint().getId() : null;
        String oldSprintName = task.getSprint() != null ? task.getSprint().getName() : null;
        Map<String, Object> oldSnapshot = buildTaskSnapshot(task);

        // Quyền: MEMBER chỉ sửa task mình là assignee; PM sửa được tất cả
        boolean isAssignee = task.getAssignee() != null
            && task.getAssignee().getId().equals(currentUserId);
        boolean isPM = actorMember != null && actorMember.getProjectRole() == ProjectRole.PROJECT_MANAGER;

        if (!isAssignee && !isPM && !isAdmin) {
            throw new Forbidden("MEMBER chỉ được sửa task mà mình là Assignee");
        }

        // Assignee (của task này), PM, hoặc ADMIN mới được đổi assignee (spec RBAC)
        if (request.getAssigneeId() != null && (isAssignee || isPM || isAdmin)) {
            if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getAssigneeId())) {
                throw new BadRequestException("Assignee không phải member dự án");
            }
            User newAssignee = getUser(request.getAssigneeId());
            // Notify new assignee if different from current
            if (task.getAssignee() == null || !task.getAssignee().getId().equals(request.getAssigneeId())) {
                notificationService.createNotification(
                    newAssignee, NotificationType.TASK_ASSIGNED,
                    "Task được giao cho bạn",
                    String.format("Bạn được giao task [%s] %s", task.getTaskCode(), task.getTitle()),
                    EntityType.TASK.name(), task.getId()
                );
            }
            task.setAssignee(newAssignee);
        }

        // Đổi cột Kanban nếu có
        if (request.getStatusColumnId() != null
                && (task.getStatusColumn() == null
                    || !task.getStatusColumn().getId().equals(request.getStatusColumnId()))) {
            ProjectStatusColumn newCol = columnRepository.findById(request.getStatusColumnId())
                .orElseThrow(() -> new NotFoundException("Column not found"));
            task.setStatusColumn(newCol);
            if (newCol.getMappedStatus() != null) {
                task.setTaskStatus(newCol.getMappedStatus());
            }
            task.setTaskPosition((int) taskRepository.countByStatusColumnId(newCol.getId()));
        }

        // Đổi sprint nếu có (BR-20: chỉ PM thêm task vào sprint ACTIVE)
        if (request.getSprintId() != null) {
            Sprint sprint = sprintRepository
                .findByIdAndProject_IdAndDeletedAtIsNull(request.getSprintId(), projectId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại hoặc không thuộc dự án này"));
            if (sprint.getStatus() == SprintStatus.COMPLETED) {
                throw new BusinessRuleException("Không thể thêm task vào sprint đã hoàn thành");
            }
            if (sprint.getStatus() == SprintStatus.ACTIVE && !isPM && !isAdmin) {
                throw new Forbidden("BR-20: Chỉ PM mới được thêm task vào sprint đang ACTIVE");
            }
            task.setSprint(sprint);
        }

        taskMapper.updateEntityFromRequest(task, request);
        task = taskRepository.save(task);

        Map<String, Object> newSnapshot = buildTaskSnapshot(task);
        logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, taskId,
            ActionType.UPDATED, toJson(oldSnapshot), toJson(newSnapshot));

        UUID newAssigneeId = task.getAssignee() != null ? task.getAssignee().getId() : null;
        String newAssigneeName = task.getAssignee() != null ? task.getAssignee().getFullName() : null;
        if ((oldAssigneeId == null && newAssigneeId != null)
                || (oldAssigneeId != null && !oldAssigneeId.equals(newAssigneeId))) {
            logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, taskId,
                    ActionType.ASSIGNEE_CHANGED,
                    toJson(mapOf("assigneeId", oldAssigneeId, "assigneeName", oldAssigneeName)),
                    toJson(mapOf("assigneeId", newAssigneeId, "assigneeName", newAssigneeName)));
        }

        String newPriority = task.getPriority() != null ? task.getPriority().name() : null;
        if ((oldPriority == null && newPriority != null) || (oldPriority != null && !oldPriority.equals(newPriority))) {
            logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, taskId,
                    ActionType.PRIORITY_CHANGED, oldPriority, newPriority);
        }

        UUID newSprintId = task.getSprint() != null ? task.getSprint().getId() : null;
        String newSprintName = task.getSprint() != null ? task.getSprint().getName() : null;
        if ((oldSprintId == null && newSprintId != null)
                || (oldSprintId != null && !oldSprintId.equals(newSprintId))) {
            logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, taskId,
                    ActionType.SPRINT_CHANGED,
                    toJson(mapOf("sprintId", oldSprintId, "sprintName", oldSprintName)),
                    toJson(mapOf("sprintId", newSprintId, "sprintName", newSprintName)));
        }

        reportService.invalidateOverviewCache(projectId);
        return taskMapper.toDetailResponse(task);
    }

    // ════════════════════════════════════════
    // P3-BE-03: UPDATE STATUS (PATCH /status)
    // ════════════════════════════════════════
    @Override
    public TaskStatusChangedResponse updateStatus(UUID projectId, UUID taskId,
                                                   UpdateTaskStatusRequest request, UUID currentUserId) {
        Task task = getTaskInProject(taskId, projectId);
        User currentUser = getUser(currentUserId);
        ProjectMember actorMember = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .orElse(null);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;
        if (actorMember == null && !isAdmin) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
        if (actorMember != null && actorMember.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không được đổi trạng thái task");
        }

        // Quyền: Assignee hoặc PM/ADMIN
        boolean isAssignee = task.getAssignee() != null
            && task.getAssignee().getId().equals(currentUserId);
        boolean isPM = actorMember != null && actorMember.getProjectRole() == ProjectRole.PROJECT_MANAGER;

        if (!isAssignee && !isPM && !isAdmin) {
            throw new Forbidden("Chỉ Assignee hoặc PM mới được đổi trạng thái");
        }

        TaskStatus oldStatus = task.getTaskStatus();
        TaskStatus newStatus = request.getStatus();

        // BR-18: Kiểm tra sub-tasks khi chuyển sang DONE
        if (newStatus == TaskStatus.DONE) {
            List<Task> pendingChildren = taskRepository.findUnfinishedSubtasks(
                taskId, List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
            if (!pendingChildren.isEmpty()) {
                List<Map<String, Object>> pendingList = pendingChildren.stream().map(st -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", st.getId().toString());
                    m.put("taskCode", st.getTaskCode());
                    m.put("title", st.getTitle());
                    m.put("taskStatus", st.getTaskStatus().name());
                    return m;
                }).toList();
                throw new com.zone.tasksphere.exception.SubtaskPendingException(pendingList);
            }

            // P3-BE-06: Kiểm tra task dependencies — tất cả blocker phải DONE/CANCELLED
            List<UUID> blockingTaskIds = dependencyRepository.findDependsOnIdsByTaskId(taskId);
            List<String> blockerCodes = blockingTaskIds.stream()
                .map(blockerId -> taskRepository.findById(blockerId).orElse(null))
                .filter(blocker -> blocker != null
                        && blocker.getTaskStatus() != TaskStatus.DONE
                        && blocker.getTaskStatus() != TaskStatus.CANCELLED)
                .map(Task::getTaskCode)
                .toList();

            if (!blockerCodes.isEmpty()) {
                throw new BusinessRuleException(
                    "Task đang bị chặn bởi task chưa hoàn thành: " + String.join(", ", blockerCodes));
            }
        }

        task.setTaskStatus(newStatus);
        task = taskRepository.save(task);

        logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, taskId,
            ActionType.STATUS_CHANGED,
            toJson(Map.of("status", oldStatus.name())),
            toJson(Map.of("status", newStatus.name())));

        // Notify reporter when task becomes DONE
        if (newStatus == TaskStatus.DONE) {
            notifyDoneToPmAndReporter(task, currentUser, oldStatus, newStatus);
        }

        // Emit WebSocket event task.status_changed
        TaskStatusChangedResponse wsPayload = TaskStatusChangedResponse.builder()
            .id(task.getId())
            .taskCode(task.getTaskCode())
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .updatedAt(task.getUpdatedAt())
            .build();
        webSocketService.sendToProject(task.getProject().getId().toString(), "task.status_changed", wsPayload);

        log.info("Task {} status changed: {} → {} by {}", task.getTaskCode(), oldStatus, newStatus, currentUserId);

        reportService.invalidateOverviewCache(projectId);
        return wsPayload;
    }

    // ════════════════════════════════════════
    // P3-BE-03: UPDATE POSITION (PATCH /position)
    // ════════════════════════════════════════
    @Override
    public void updatePosition(UUID projectId, UUID taskId,
                               UpdateTaskPositionRequest request, UUID currentUserId) {
        validateMembership(projectId, currentUserId);
        Task task = getTaskInProject(taskId, projectId);
        int oldPosition = task.getTaskPosition();
        UUID oldColumnId = task.getStatusColumn() != null ? task.getStatusColumn().getId() : null;
        String oldColumnName = task.getStatusColumn() != null ? task.getStatusColumn().getName() : null;

        ProjectStatusColumn newColumn = columnRepository.findById(request.getStatusColumnId())
            .orElseThrow(() -> new NotFoundException("Column not found"));

        // Dịch chuyển các task khác trong cột để nhường chỗ
        taskRepository.shiftPositionsDown(newColumn.getId(), request.getNewPosition(), taskId);

        task.setStatusColumn(newColumn);
        task.setTaskPosition(request.getNewPosition());
        if (newColumn.getMappedStatus() != null) {
            TaskStatus mapped = newColumn.getMappedStatus();
            task.setTaskStatus(mapped);
        }
        taskRepository.save(task);

        logActivity(projectId, currentUserId, EntityType.TASK, taskId,
                ActionType.POSITION_CHANGED,
                toJson(mapOf("columnId", oldColumnId, "columnName", oldColumnName, "position", oldPosition)),
                toJson(mapOf("columnId", newColumn.getId(), "columnName", newColumn.getName(), "position", request.getNewPosition())));

        // FIX: P5-BE-07 - Emit WebSocket event task.position_updated
        webSocketService.sendToProject(task.getProject().getId().toString(), "task.position_updated",
            java.util.Map.of(
                "taskId", task.getId(),
                "taskCode", task.getTaskCode(),
                "columnId", newColumn.getId(),
                "newPosition", request.getNewPosition()
            ));

        log.info("Task {} repositioned to column={} pos={}", task.getTaskCode(),
            newColumn.getName(), request.getNewPosition());
    }

    // ════════════════════════════════════════
    // P3-BE-04: DELETE TASK (soft delete)
    // ════════════════════════════════════════
    @Override
    public void deleteTask(UUID projectId, UUID taskId, UUID currentUserId) {
        User currentUser = getUser(currentUserId);
        boolean isPM = isMemberPM(projectId, currentUserId);
        if (!isPM && currentUser.getSystemRole() != SystemRole.ADMIN) {
            throw new Forbidden("Chỉ PM hoặc Admin mới có quyền xoá task");
        }

        Task task = getTaskInProject(taskId, projectId);
        Instant now = Instant.now();

        task.setDeletedAt(now);
        taskRepository.save(task);

        // BR-24: Đệ quy soft delete sub-tasks
        softDeleteSubtasksRecursively(projectId, currentUserId, taskId, now);

        logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, taskId,
            ActionType.DELETED, toJson(Map.of(
                    "taskCode", task.getTaskCode(),
                    "title", task.getTitle(),
                    "status", task.getTaskStatus() != null ? task.getTaskStatus().name() : null
            )), toJson(Map.of("deletedAt", now.toString())));

        log.info("Task {} soft-deleted by {}", task.getTaskCode(), currentUserId);
        reportService.invalidateOverviewCache(projectId);
    }

    // ════════════════════════════════════════
    // P3-BE-05: SUB-TASK
    // ════════════════════════════════════════

    @Override
    public TaskDetailResponse createSubTask(UUID parentTaskId, CreateTaskRequest request, UUID currentUserId) {
        Task subTask = createSubTaskInternal(parentTaskId, request, currentUserId);
        return taskMapper.toDetailResponse(subTask);
    }

    @Override
    public SubTaskResponse createSubTaskLight(UUID parentTaskId, CreateTaskRequest request, UUID currentUserId) {
        Task subTask = createSubTaskInternal(parentTaskId, request, currentUserId);
        return toSubTaskResponse(subTask);
    }

    /** Logic chung tạo sub-task — trả về entity đã save (không map response) */
    private Task createSubTaskInternal(UUID parentTaskId, CreateTaskRequest request, UUID currentUserId) {
        Task parentTask = taskRepository.findById(parentTaskId)
            .orElseThrow(() -> new NotFoundException("Parent task not found: " + parentTaskId));

        if (request.getType() == TaskType.EPIC) {
            throw new BusinessRuleException("EPIC không thể là sub-task");
        }
        if (parentTask.getType() == TaskType.EPIC) {
            throw new BusinessRuleException("Không thể tạo sub-task dưới Epic. Epic không hỗ trợ sub-task.");
        }

        int newDepth = parentTask.getDepth() + 1;
        // Unlimited depth — no BR-15 check per xlsx spec

        if (parentTask.getProject() == null) {
            throw new NotFoundException("Parent task không thuộc dự án hợp lệ");
        }
        UUID projectId = parentTask.getProject().getId();
        ProjectMember member = getMember(projectId, currentUserId);
        if (member.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không được tạo task");
        }

        User currentUser = getUser(currentUserId);
        User assignee = null;
        if (request.getAssigneeId() != null) {
            assignee = getUser(request.getAssigneeId());
            if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getAssigneeId())) {
                throw new BadRequestException("Assignee không phải thành viên dự án");
            }
        }

        ProjectStatusColumn statusColumn = getOrCreateDefaultColumn(parentTask.getProject());
        String taskCode = taskCodeGenerator.generateTaskCode(parentTask.getProject());
        int position = (int) taskRepository.countByStatusColumnId(statusColumn.getId());

        Task subTask = Task.builder()
            .taskCode(taskCode)
            .title(request.getTitle())
            .description(request.getDescription())
            .type(request.getType() != null ? request.getType() : TaskType.TASK)
            .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
            .taskStatus(statusColumn.getMappedStatus() != null ? statusColumn.getMappedStatus() : TaskStatus.TODO)
            .storyPoints(request.getStoryPoints())
            .estimatedHours(request.getEstimatedHours())
            .dueDate(request.getDueDate())
            .taskPosition(position)
            .depth(newDepth)
            .project(parentTask.getProject())
            .assignee(assignee)
            .reporter(currentUser)
            .sprint(parentTask.getSprint())
            .statusColumn(statusColumn)
            .parentTask(parentTask)
            .build();

        subTask = taskRepository.save(subTask);
        logActivity(projectId, currentUserId, EntityType.TASK, subTask.getId(), ActionType.SUBTASK_CREATED, null, toJson(Map.of(
                "taskCode", taskCode,
                "title", subTask.getTitle(),
                "parentTaskId", parentTask.getId()
        )));
        log.info("Sub-task created: {} under parent {}", taskCode, parentTask.getTaskCode());
        return subTask;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubTaskResponse> getSubTasks(UUID taskId, UUID currentUserId) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));

        validateMembership(task.getProject().getId(), currentUserId);

        List<Task> children = taskRepository.findByParentTaskId(taskId);
        return children.stream().map(this::toSubTaskResponse).toList();
    }

    @Override
    public TaskDetailResponse promoteSubTask(UUID subtaskId, UUID currentUserId) {
        Task subTask = taskRepository.findById(subtaskId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + subtaskId));

        UUID projectId = subTask.getProject().getId();
        validateMembership(projectId, currentUserId);

        if (subTask.getParentTask() == null) {
            throw new BadRequestException("Task này không phải sub-task");
        }

        Task oldParent = subTask.getParentTask();
        UUID oldParentId = oldParent.getId();

        subTask.setParentTask(null);
        subTask.setDepth(0);
        final Task promotedTask = taskRepository.save(subTask);

        logActivity(projectId, currentUserId, EntityType.TASK, promotedTask.getId(),
            ActionType.SUBTASK_PROMOTED,
            toJson(Map.of("parentTaskId", oldParentId, "parentTaskCode", oldParent.getTaskCode())),
            toJson(Map.of("taskCode", promotedTask.getTaskCode(), "title", promotedTask.getTitle())));

        // Notify: PM(s) + reporter of old parent + assignee of sub-task
        String notifTitle = "Sub-task được chuyển thành task độc lập";
        String notifBody = String.format("[%s] %s đã được tách ra từ [%s]",
            promotedTask.getTaskCode(), promotedTask.getTitle(), oldParent.getTaskCode());

        projectMemberRepository.findByProjectId(projectId).stream()
            .filter(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
            .map(ProjectMember::getUser)
            .filter(u -> !u.getId().equals(currentUserId))
            .forEach(pm -> notificationService.createNotification(
                pm, NotificationType.TASK_ASSIGNED, notifTitle, notifBody,
                EntityType.TASK.name(), promotedTask.getId()));

        if (oldParent.getReporter() != null && !oldParent.getReporter().getId().equals(currentUserId)) {
            notificationService.createNotification(
                oldParent.getReporter(), NotificationType.TASK_ASSIGNED, notifTitle, notifBody,
                EntityType.TASK.name(), promotedTask.getId());
        }
        if (promotedTask.getAssignee() != null && !promotedTask.getAssignee().getId().equals(currentUserId)) {
            notificationService.createNotification(
                promotedTask.getAssignee(), NotificationType.TASK_ASSIGNED, notifTitle, notifBody,
                EntityType.TASK.name(), promotedTask.getId());
        }

        log.info("Sub-task {} promoted to root task by {}", promotedTask.getTaskCode(), currentUserId);
        return taskMapper.toDetailResponse(promotedTask);
    }

    // ════════════════════════════════════════
    // P3-BE-10: CALENDAR VIEW
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public CalendarViewResponse getCalendarView(UUID projectId, int year, int month, UUID currentUserId) {
        validateMembership(projectId, currentUserId);

        if (year < 2020 || year > 2030) {
            throw new BadRequestException("Năm không hợp lệ (2020–2030)");
        }
        if (month < 1 || month > 12) {
            throw new BadRequestException("Tháng không hợp lệ (1–12)");
        }

        LocalDate today = LocalDate.now();
        List<Task> tasks = taskRepository.findByProjectAndYearAndMonth(projectId, year, month);

        List<CalendarViewResponse.CalendarTaskItem> items = tasks.stream().map(t -> {
            CalendarViewResponse.UserSummary assignee = null;
            if (t.getAssignee() != null) {
                assignee = CalendarViewResponse.UserSummary.builder()
                        .id(t.getAssignee().getId())
                        .fullName(t.getAssignee().getFullName())
                        .avatarUrl(t.getAssignee().getAvatarUrl())
                        .build();
            }

            boolean isOverdue = t.getDueDate() != null
                    && t.getDueDate().isBefore(today)
                    && t.getTaskStatus() != TaskStatus.DONE
                    && t.getTaskStatus() != TaskStatus.CANCELLED;

            return CalendarViewResponse.CalendarTaskItem.builder()
                    .id(t.getId())
                    .taskCode(t.getTaskCode())
                    .title(t.getTitle())
                    .priority(t.getPriority())
                    .taskStatus(t.getTaskStatus())
                    .dueDate(t.getDueDate())
                    .columnName(t.getStatusColumn() != null ? t.getStatusColumn().getName() : null)
                    .columnColor(t.getStatusColumn() != null ? t.getStatusColumn().getColorHex() : null)
                    .isOverdue(isOverdue)
                    .assignee(assignee)
                    .build();
        }).toList();

        return CalendarViewResponse.builder()
                .year(year)
                .month(month)
                .totalTasks(items.size())
                .tasks(items)
                .build();
    }

    // ════════════════════════════════════════
    // OPTIMISTIC LOCKING
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public void validateETag(UUID taskId, String ifMatch) {
        Task task = taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
        String currentETag = "\"" + task.getVersion() + "\"";
        if (!currentETag.equals(ifMatch)) {
            throw new BadRequestException("ETag mismatch — dữ liệu đã thay đổi, vui lòng tải lại");
        }
    }

    // ════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════

    private SubTaskResponse toSubTaskResponse(Task task) {
        SubTaskResponse.UserSummary assignee = null;
        if (task.getAssignee() != null) {
            assignee = SubTaskResponse.UserSummary.builder()
                .id(task.getAssignee().getId())
                .fullName(task.getAssignee().getFullName())
                .avatarUrl(task.getAssignee().getAvatarUrl())
                .build();
        }
        int subtaskCount = task.getChildTasks() != null ? task.getChildTasks().size() : 0;
        int completedSubtaskCount = task.getChildTasks() != null
            ? (int) task.getChildTasks().stream()
                .filter(c -> c.getTaskStatus() == TaskStatus.DONE || c.getTaskStatus() == TaskStatus.CANCELLED)
                .count()
            : 0;

        return SubTaskResponse.builder()
            .id(task.getId())
            .taskCode(task.getTaskCode())
            .title(task.getTitle())
            .taskStatus(task.getTaskStatus())
            .priority(task.getPriority())
            .assignee(assignee)
            .dueDate(task.getDueDate())
            .depth(task.getDepth())
            .subtaskCount(subtaskCount)
            .completedSubtaskCount(completedSubtaskCount)
            .build();
    }

    private void softDeleteSubtasksRecursively(UUID projectId, UUID actorId, UUID parentId, Instant now) {
        List<Task> children = taskRepository.findByParentTaskId(parentId);
        for (Task child : children) {
            logActivity(projectId, actorId, EntityType.TASK, child.getId(),
                ActionType.SUBTASK_DELETED, child.getTitle(), null);
        }
        taskRepository.softDeleteDirectSubtasks(parentId, now);
        children.forEach(child -> softDeleteSubtasksRecursively(projectId, actorId, child.getId(), now));
    }

    private void validateMembership(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
    }

    private ProjectMember getMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
    }

    private boolean isMemberPM(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .map(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
            .orElse(false);
    }

    private Task getTaskInProject(UUID taskId, UUID projectId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    private Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    /**
     * Tầng 3 Safety Guard: Lấy cột đầu tiên của project.
     * Nếu project chưa có column nào (edge case) → tự seed rồi trả về cột đầu tiên.
     */
    private ProjectStatusColumn getOrCreateDefaultColumn(Project project) {
        return columnRepository
            .findFirstByProjectOrderBySortOrderAsc(project)
            .orElseGet(() -> {
                log.warn("[SafeGuard] Project {} có no columns! Auto-seeding...", project.getId());
                List<ProjectStatusColumn> seeded = defaultColumnSeeder.seedForProject(project);
                return seeded.get(0);
            });
    }

    private void logActivity(UUID projectId, UUID actorId, EntityType entityType,
                              UUID entityId, ActionType action, String oldVal, String newVal) {
        try {
            HttpServletRequest httpRequest = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, entityType, entityId,
                action, oldVal, newVal, httpRequest);
        } catch (Exception e) {
            log.warn("Failed to log activity for task {}: {}", entityId, e.getMessage());
        }
    }

    private void notifyDoneToPmAndReporter(Task task, User currentUser, TaskStatus oldStatus, TaskStatus newStatus) {
        User pm = projectMemberRepository.findFirstByProjectIdAndProjectRoleOrderByJoinedAtAsc(
                task.getProject().getId(), ProjectRole.PROJECT_MANAGER)
                .map(ProjectMember::getUser)
                .orElse(null);

        if (pm != null && !pm.getId().equals(currentUser.getId())) {
            notificationService.createNotification(
                    pm,
                    NotificationType.TASK_STATUS_CHANGED,
                    "Task hoàn thành",
                    currentUser.getFullName() + " đã hoàn thành \"" + task.getTitle() + "\"",
                    EntityType.TASK.name(),
                    task.getId()
            );
            webSocketService.sendToUser(pm.getId(), "/queue/task_done", Map.of(
                    "taskId", task.getId(),
                    "taskCode", task.getTaskCode(),
                    "title", task.getTitle(),
                    "projectId", task.getProject().getId(),
                    "completedBy", currentUser.getFullName(),
                    "completedAt", Instant.now().toString()
            ));
        }

        User reporter = task.getReporter();
        if (reporter != null
                && !reporter.getId().equals(currentUser.getId())
                && (pm == null || !reporter.getId().equals(pm.getId()))) {
            notificationService.sendTaskStatusChanged(task, reporter, oldStatus.name(), newStatus.name());
        }
    }

    private String toJson(Object payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Cannot serialize activity payload: {}", e.getMessage());
            return String.valueOf(payload);
        }
    }

    private Map<String, Object> buildTaskSnapshot(Task task) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", task.getTitle());
        data.put("description", task.getDescription());
        data.put("type", task.getType() != null ? task.getType().name() : null);
        data.put("priority", task.getPriority() != null ? task.getPriority().name() : null);
        data.put("status", task.getTaskStatus() != null ? task.getTaskStatus().name() : null);
        data.put("assigneeId", task.getAssignee() != null ? task.getAssignee().getId() : null);
        data.put("assigneeName", task.getAssignee() != null ? task.getAssignee().getFullName() : null);
        data.put("sprintId", task.getSprint() != null ? task.getSprint().getId() : null);
        data.put("sprintName", task.getSprint() != null ? task.getSprint().getName() : null);
        data.put("columnId", task.getStatusColumn() != null ? task.getStatusColumn().getId() : null);
        data.put("dueDate", task.getDueDate());
        data.put("storyPoints", task.getStoryPoints());
        return data;
    }

    private Map<String, Object> mapOf(Object... kvPairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return map;
    }
}
