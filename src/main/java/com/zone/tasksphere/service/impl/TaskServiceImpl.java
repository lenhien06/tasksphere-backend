package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.component.DefaultColumnSeeder;
import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.mapper.TaskMapper;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.service.ReportService;
import com.zone.tasksphere.service.TaskService;
import com.zone.tasksphere.specification.TaskSpecification;
import com.zone.tasksphere.utils.TaskCodeGenerator;
import com.zone.tasksphere.utils.TaskFilterSupport;
import com.zone.tasksphere.utils.SkillTaxonomy;
import com.zone.tasksphere.repository.TaskDependencyRepository;
import com.zone.tasksphere.repository.ChecklistItemRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final WorkspaceMemberRepository workspaceMemberRepository;
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
            requireActiveSprintConfirmation(sprint, request.getConfirmActiveSprintChange());
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
        LocalDate scheduledStart = resolveRequestedStartDate(request.getStartDate(), sprint, null);
        LocalDate scheduledEnd = resolveRequestedEndDate(request.getEndDate(), request.getDueDate(), scheduledStart, null);
        validateScheduleWindow(scheduledStart, scheduledEnd);
        validateScheduledWindowWithinSprint(sprint, scheduledStart, scheduledEnd);

        // Build và save entity
        Task task = Task.builder()
            .taskCode(taskCode)
            .title(request.getTitle())
            .description(request.getDescription())
            .type(request.getType() != null ? request.getType() : TaskType.TASK)
            .priority(request.getPriority() != null ? request.getPriority() : TaskPriority.MEDIUM)
            .taskStatus(statusColumn.getMappedStatus() != null ? statusColumn.getMappedStatus() : TaskStatus.TODO)
            .completedAt(statusColumn.getMappedStatus() == TaskStatus.DONE ? Instant.now() : null)
            .storyPoints(request.getStoryPoints())
            .estimatedHours(request.getEstimatedHours())
            .startDate(scheduledStart)
            .endDate(scheduledEnd)
            .skillTagsRequired(request.getSkillTagsRequired())
            .startDate(scheduledStart)
            .endDate(scheduledEnd)
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
        syncWorkspaceMemberActiveTaskCount(project, assignee != null ? assignee.getId() : null);

        // Ghi activity log
        logActivity(project.getId(), currentUserId, EntityType.TASK, task.getId(),
            ActionType.TASK_CREATED, null, toJson(Map.of(
                    "taskCode", taskCode,
                    "title", task.getTitle(),
                    "type", task.getType() != null ? task.getType().name() : null,
                    "priority", task.getPriority() != null ? task.getPriority().name() : null
            )));

        if (sprint != null && sprint.getStatus() == SprintStatus.ACTIVE) {
            logActivity(project.getId(), currentUserId, EntityType.SPRINT, sprint.getId(),
                ActionType.UPDATED, null, toJson(mapOf(
                    "activeSprintScopeChange", true,
                    "taskId", task.getId(),
                    "taskCode", task.getTaskCode(),
                    "taskTitle", task.getTitle(),
                    "actorName", currentUser.getFullName(),
                    "message", String.format("PM %s da them Task %s vao luc Sprint dang chay.", currentUser.getFullName(), task.getTitle())
                )));
        }

        // Gửi notification nếu có assignee khác reporter
        if (assignee != null && !assignee.getId().equals(currentUserId)) {
            notificationService.sendTaskAssigned(task, assignee, currentUser);
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
        TaskFilterParams normalizedParams = TaskFilterSupport.resolveForQuery(params, currentUserId);
        normalizedParams.setProjectId(projectId);
        Specification<Task> spec = TaskSpecification.buildFilter(normalizedParams);
        Page<Task> page = taskRepository.findAll(spec, pageable);
        List<TaskResponse> responses = enrichTaskResponsesWithDependencyState(projectId, page.getContent());
        return PageResponse.<TaskResponse>builder()
                .content(responses)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .size(page.getSize())
                .number(page.getNumber())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }

    // ════════════════════════════════════════
    // P3-BE-02: GET TASK DETAIL
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskById(UUID projectId, UUID taskId, UUID currentUserId) {
        validateMembership(projectId, currentUserId);
        Task task = getTaskInProject(taskId, projectId);
        return enrichTaskDetailResponse(task, currentUserId);
    }

    private List<TaskResponse> enrichTaskResponsesWithDependencyState(UUID projectId, List<Task> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }

        Set<UUID> taskIds = tasks.stream().map(Task::getId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<TaskResponse.DependencySummary>> activeBlockersByTaskId = new HashMap<>();

        for (TaskDependency edge : dependencyRepository.findBlockingEdgesByProjectId(projectId)) {
            Task blocked = edge.getBlockedTask();
            Task blocker = edge.getBlockingTask();
            if (blocked == null || blocker == null || !taskIds.contains(blocked.getId())) {
                continue;
            }
            if (blocker.getTaskStatus() == TaskStatus.DONE || blocker.getTaskStatus() == TaskStatus.CANCELLED) {
                continue;
            }
            activeBlockersByTaskId
                    .computeIfAbsent(blocked.getId(), ignored -> new ArrayList<>())
                    .add(TaskResponse.DependencySummary.builder()
                            .taskId(blocker.getId())
                            .taskCode(blocker.getTaskCode())
                            .title(blocker.getTitle())
                            .taskStatus(blocker.getTaskStatus())
                            .build());
        }

        return tasks.stream().map(task -> {
            TaskResponse response = taskMapper.toResponse(task);
            List<TaskResponse.DependencySummary> activeBlockers = activeBlockersByTaskId.getOrDefault(task.getId(), List.of());
            response.setBlockedBy(activeBlockers);
            response.setBlockedByDependency(!activeBlockers.isEmpty());
            response.setBlockingDependencyCount(activeBlockers.size());
            return response;
        }).toList();
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
        TaskStatus oldStatus = task.getTaskStatus();
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
                notificationService.sendTaskAssigned(task, newAssignee, currentUser);
            }
            task.setAssignee(newAssignee);
        }

        // Đổi cột Kanban nếu có
        if (request.getStatusColumnId() != null
                && (task.getStatusColumn() == null
                    || !task.getStatusColumn().getId().equals(request.getStatusColumnId()))) {
            ProjectStatusColumn newCol = columnRepository.findById(request.getStatusColumnId())
                .orElseThrow(() -> new NotFoundException("Column not found"));
            TaskStatus oldStatusForColumnChange = task.getTaskStatus();
            task.setStatusColumn(newCol);
            if (newCol.getMappedStatus() != null) {
                enforceQaWorkflowTransition(task, oldStatusForColumnChange, newCol.getMappedStatus(), actorMember, currentUser);
                task.setTaskStatus(newCol.getMappedStatus());
                syncCompletedAt(task, oldStatusForColumnChange, newCol.getMappedStatus());
            }
            task.setTaskPosition((int) taskRepository.countByStatusColumnId(newCol.getId()));
        }

        Sprint effectiveSprint = task.getSprint();

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
            effectiveSprint = sprint;
        }

        if (request.getDueDate() != null) {
            validateDueDateWithinSprint(effectiveSprint, request.getDueDate());
        }

        LocalDate requestedStart = resolveRequestedStartDate(request.getStartDate(), effectiveSprint, task);
        LocalDate requestedEnd = resolveRequestedEndDate(request.getEndDate(), request.getDueDate(), requestedStart, task);
        if (request.getStartDate() != null || request.getEndDate() != null) {
            validateScheduleWindow(requestedStart, requestedEnd);
            validateStartDateAgainstDependencies(task, requestedStart);
            validateScheduledWindowWithinSprint(effectiveSprint, requestedStart, requestedEnd);
            validateDependentSchedules(task, requestedEnd, Set.of(task.getId()), false);
        }

        taskMapper.updateEntityFromRequest(task, request);
        task = taskRepository.save(task);
        syncWorkspaceMemberActiveTaskCounts(task.getProject(), oldAssigneeId, task.getAssignee() != null ? task.getAssignee().getId() : null);

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

        if (oldStatus != task.getTaskStatus()) {
            notifyTaskStatusStakeholders(task, currentUser, oldStatus, task.getTaskStatus());
        }

        reportService.invalidateOverviewCache(projectId);
        return enrichTaskDetailResponse(task, currentUserId);
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

        // Quyền: Member, PM hoặc Admin (Viewer đã bị chặn ở trên)
        boolean isMember = actorMember != null && actorMember.getProjectRole() == ProjectRole.MEMBER;
        boolean isPM = actorMember != null && actorMember.getProjectRole() == ProjectRole.PROJECT_MANAGER;

        if (!isMember && !isPM && !isAdmin) {
            throw new Forbidden("Chỉ Member, PM hoặc Admin mới được đổi trạng thái");
        }

        TaskStatus oldStatus = task.getTaskStatus();
        TaskStatus newStatus = request.getStatus();

        if (newStatus == TaskStatus.DONE) {
            assertAllDescendantSubtasksDone(taskId);
            assertNoUnfinishedBlockingDependencies(taskId);
        }

        enforceQaWorkflowTransition(task, oldStatus, newStatus, actorMember, currentUser);

        task.setTaskStatus(newStatus);
        syncCompletedAt(task, oldStatus, newStatus);

        if (request.getStatusColumnId() != null) {
            ProjectStatusColumn requestedColumn = columnRepository.findById(request.getStatusColumnId())
                .orElseThrow(() -> new NotFoundException("Column not found"));
            if (!requestedColumn.getProject().getId().equals(projectId)) {
                throw new BadRequestException("Cột không thuộc dự án hiện tại");
            }
            task.setStatusColumn(requestedColumn);
        } else {
            // Sync statusColumn to the first column mapped to the new status so Kanban grouping stays correct.
            // Older projects often have only To Do marked as default.
            columnRepository.findFirstByProjectIdAndMappedStatusOrderBySortOrderAsc(projectId, newStatus)
                .ifPresent(task::setStatusColumn);
        }

        task = taskRepository.save(task);

        // BR-AI-06: Decrement active_task_count when assignee's task reaches terminal state
        boolean enteringTerminal = (newStatus == TaskStatus.DONE || newStatus == TaskStatus.CANCELLED)
                && oldStatus != TaskStatus.DONE && oldStatus != TaskStatus.CANCELLED;
        if (enteringTerminal && task.getAssignee() != null) {
            projectMemberRepository.findByProjectIdAndUserId(projectId, task.getAssignee().getId())
                    .ifPresent(pm -> {
                        pm.setActiveTaskCount(Math.max(0, pm.getActiveTaskCount() - 1));
                        projectMemberRepository.save(pm);
                    });
        }
        syncWorkspaceMemberActiveTaskCount(task.getProject(), task.getAssignee() != null ? task.getAssignee().getId() : null);

        logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, taskId,
            ActionType.STATUS_CHANGED,
            toJson(Map.of("status", oldStatus.name())),
            toJson(Map.of("status", newStatus.name())));

        notifyTaskStatusStakeholders(task, currentUser, oldStatus, newStatus);

        // Emit WebSocket event task.status_changed
        TaskStatusChangedResponse wsPayload = TaskStatusChangedResponse.builder()
            .id(task.getId())
            .taskCode(task.getTaskCode())
            .oldStatus(oldStatus)
            .newStatus(newStatus)
            .updatedAt(task.getUpdatedAt())
            .columnId(task.getStatusColumn() != null ? task.getStatusColumn().getId() : null)
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
        User currentUser = getUser(currentUserId);
        ProjectMember actorMember = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .orElse(null);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;
        if (actorMember == null && !isAdmin) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
        if (actorMember != null && actorMember.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không được kéo thả task");
        }
        int oldPosition = task.getTaskPosition();
        UUID oldColumnId = task.getStatusColumn() != null ? task.getStatusColumn().getId() : null;
        String oldColumnName = task.getStatusColumn() != null ? task.getStatusColumn().getName() : null;
        TaskStatus oldStatus = task.getTaskStatus();

        ProjectStatusColumn newColumn = columnRepository.findById(request.getStatusColumnId())
            .orElseThrow(() -> new NotFoundException("Column not found"));
        if (newColumn.getProject() == null || !projectId.equals(newColumn.getProject().getId())) {
            throw new BadRequestException("Column does not belong to the current project");
        }

        UUID sourceColumnId = task.getStatusColumn() != null ? task.getStatusColumn().getId() : null;
        List<Task> sourceTasks = sourceColumnId != null
                ? new ArrayList<>(taskRepository.findByProjectIdAndStatusColumnIdOrderByTaskPositionAsc(projectId, sourceColumnId))
                : new ArrayList<>();
        List<Task> targetTasks = sourceColumnId != null && sourceColumnId.equals(newColumn.getId())
                ? sourceTasks
                : new ArrayList<>(taskRepository.findByProjectIdAndStatusColumnIdOrderByTaskPositionAsc(projectId, newColumn.getId()));

        sourceTasks.removeIf(item -> item.getId().equals(taskId));
        if (targetTasks != sourceTasks) {
            targetTasks.removeIf(item -> item.getId().equals(taskId));
        }

        int boundedPosition = Math.max(0, request.getNewPosition());
        if (boundedPosition > targetTasks.size()) {
            boundedPosition = targetTasks.size();
        }

        // Dịch chuyển các task khác trong cột để nhường chỗ

        task.setStatusColumn(newColumn);
        task.setTaskPosition(boundedPosition);
        if (newColumn.getMappedStatus() != null) {
            TaskStatus currentStatusBeforeMove = task.getTaskStatus();
            TaskStatus mapped = newColumn.getMappedStatus();
            if (mapped == TaskStatus.DONE) {
                assertAllDescendantSubtasksDone(taskId);
                assertNoUnfinishedBlockingDependencies(taskId);
            }
            enforceQaWorkflowTransition(task, currentStatusBeforeMove, mapped, actorMember, currentUser);
            task.setTaskStatus(mapped);
            syncCompletedAt(task, currentStatusBeforeMove, mapped);
            if (mapped == TaskStatus.TESTING && request.getTransitionEvidence() != null && !request.getTransitionEvidence().isBlank()) {
                logActivity(projectId, currentUserId, EntityType.TASK, taskId,
                        ActionType.UPDATED,
                        null,
                        toJson(mapOf("testingHandoffEvidence", request.getTransitionEvidence().trim())));
            }
            // BR-AI-06: Decrement active_task_count when task dragged to terminal column
            boolean enteringTerminal = (mapped == TaskStatus.DONE || mapped == TaskStatus.CANCELLED)
                    && currentStatusBeforeMove != TaskStatus.DONE && currentStatusBeforeMove != TaskStatus.CANCELLED;
            if (enteringTerminal && task.getAssignee() != null) {
                projectMemberRepository.findByProjectIdAndUserId(projectId, task.getAssignee().getId())
                        .ifPresent(pm -> {
                            pm.setActiveTaskCount(Math.max(0, pm.getActiveTaskCount() - 1));
                            projectMemberRepository.save(pm);
                        });
            }
        }
        targetTasks.add(boundedPosition, task);
        reindexTasks(sourceTasks);
        if (targetTasks != sourceTasks) {
            reindexTasks(targetTasks);
            taskRepository.saveAll(sourceTasks);
        }
        taskRepository.saveAll(targetTasks);
        syncWorkspaceMemberActiveTaskCount(task.getProject(), task.getAssignee() != null ? task.getAssignee().getId() : null);

        if (oldStatus != task.getTaskStatus()) {
            logActivity(projectId, currentUserId, EntityType.TASK, taskId,
                    ActionType.STATUS_CHANGED,
                    toJson(Map.of("status", oldStatus.name())),
                    toJson(Map.of("status", task.getTaskStatus().name())));
            notifyTaskStatusStakeholders(task, currentUser, oldStatus, task.getTaskStatus());
            TaskStatusChangedResponse wsPayload = TaskStatusChangedResponse.builder()
                    .id(task.getId())
                    .taskCode(task.getTaskCode())
                    .oldStatus(oldStatus)
                    .newStatus(task.getTaskStatus())
                    .updatedAt(task.getUpdatedAt())
                    .columnId(task.getStatusColumn() != null ? task.getStatusColumn().getId() : null)
                    .build();
            webSocketService.sendToProject(task.getProject().getId().toString(), "task.status_changed", wsPayload);
        }

        logActivity(projectId, currentUserId, EntityType.TASK, taskId,
                ActionType.POSITION_CHANGED,
                toJson(mapOf("columnId", oldColumnId, "columnName", oldColumnName, "position", oldPosition)),
                toJson(mapOf("columnId", newColumn.getId(), "columnName", newColumn.getName(), "position", boundedPosition)));

        // FIX: P5-BE-07 - Emit WebSocket event task.position_updated
        webSocketService.sendToProject(task.getProject().getId().toString(), "task.position_updated",
            java.util.Map.of(
                "taskId", task.getId(),
                "taskCode", task.getTaskCode(),
                "columnId", newColumn.getId(),
                "newPosition", boundedPosition
            ));

        log.info("Task {} repositioned to column={} pos={}", task.getTaskCode(),
            newColumn.getName(), boundedPosition);
        reportService.invalidateOverviewCache(projectId);
    }

    @Override
    @Transactional
    public TaskDetailResponse updateDueDate(UUID projectId, UUID taskId,
                                            UpdateTaskDueDateRequest request, UUID currentUserId) {
        ProjectMember actorMember = projectMemberRepository
            .findByProjectIdAndUserId(projectId, currentUserId)
            .orElseThrow(() -> new Forbidden("Ban khong phai thanh vien du an nay"));
        if (actorMember.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER khong duoc sua task");
        }

        Task task = getTaskInProject(taskId, projectId);
        if (task.getDeletedAt() != null) {
            throw new NotFoundException("Task khong ton tai");
        }

        LocalDate newDueDate = request.getDueDate();
        validateDueDateWithinSprint(task.getSprint(), newDueDate);

        LocalDate oldDueDate = task.getDueDate();
        task.setDueDate(newDueDate);
        taskRepository.save(task);

        logActivity(projectId, currentUserId, EntityType.TASK, taskId,
                ActionType.UPDATED,
                oldDueDate == null ? null : oldDueDate.toString(),
                newDueDate.toString());

        return enrichTaskDetailResponse(task, currentUserId);
    }

    @Override
    @Transactional
    public ShiftTaskScheduleResponse shiftTaskSchedule(UUID projectId, UUID taskId,
                                                       ShiftTaskScheduleRequest request, UUID currentUserId) {
        Task rootTask = getTaskInProject(taskId, projectId);
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
        boolean isAssignee = rootTask.getAssignee() != null
                && rootTask.getAssignee().getId().equals(currentUserId);
        boolean isPM = actorMember != null && actorMember.getProjectRole() == ProjectRole.PROJECT_MANAGER;
        if (!isAssignee && !isPM && !isAdmin) {
            throw new Forbidden("MEMBER chỉ được sửa task mà mình là Assignee");
        }

        int shiftDays = request.getShiftDays();
        boolean autoShiftDependents = Boolean.TRUE.equals(request.getAutoShiftDependents());
        if (shiftDays == 0) {
            return ShiftTaskScheduleResponse.builder()
                    .taskId(taskId)
                    .shiftDays(0)
                    .autoShiftDependents(autoShiftDependents)
                    .updatedTaskIds(List.of(taskId))
                    .affectedTasks(1)
                    .build();
        }

        LinkedHashSet<UUID> shiftedTaskIds = new LinkedHashSet<>();
        shiftedTaskIds.add(taskId);
        if (autoShiftDependents) {
            collectDependentTaskIds(taskId, shiftedTaskIds);
        }

        List<Task> tasksToShift = taskRepository.findAllById(shiftedTaskIds);
        Map<UUID, LocalDate> shiftedStarts = new HashMap<>();
        Map<UUID, LocalDate> shiftedEnds = new HashMap<>();

        for (Task task : tasksToShift) {
            LocalDate currentStart = resolveTimelineStartDate(task);
            LocalDate currentEnd = resolveTimelineEndDate(task, currentStart);
            LocalDate nextStart = currentStart.plusDays(shiftDays);
            LocalDate nextEnd = currentEnd.plusDays(shiftDays);

            validateScheduleWindow(nextStart, nextEnd);
            validateScheduledWindowWithinSprint(task.getSprint(), nextStart, nextEnd);

            shiftedStarts.put(task.getId(), nextStart);
            shiftedEnds.put(task.getId(), nextEnd);
        }

        for (Task task : tasksToShift) {
            validateStartDateAgainstDependencies(task, shiftedStarts.get(task.getId()), shiftedTaskIds, shiftedEnds);
            validateDependentSchedules(task, shiftedEnds.get(task.getId()), shiftedTaskIds, autoShiftDependents);
        }

        for (Task task : tasksToShift) {
            task.setStartDate(shiftedStarts.get(task.getId()));
            task.setEndDate(shiftedEnds.get(task.getId()));
        }
        taskRepository.saveAll(tasksToShift);
        reportService.invalidateOverviewCache(projectId);

        List<UUID> updatedTaskIds = new ArrayList<>(shiftedTaskIds);
        logActivity(projectId, currentUserId, EntityType.TASK, taskId,
                ActionType.UPDATED, null, toJson(Map.of(
                        "shiftDays", shiftDays,
                        "autoShiftDependents", autoShiftDependents,
                        "updatedTaskIds", updatedTaskIds
                )));

        return ShiftTaskScheduleResponse.builder()
                .taskId(taskId)
                .shiftDays(shiftDays)
                .autoShiftDependents(autoShiftDependents)
                .updatedTaskIds(updatedTaskIds)
                .affectedTasks(updatedTaskIds.size())
                .build();
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
        List<UUID> deletedTaskIds = collectTaskTreeIds(taskId);
        Set<UUID> affectedWorkspaceAssignees = collectTaskTreeAssigneeIds(taskId);

        dependencyRepository.deleteAllByTaskIds(deletedTaskIds);

        task.setDeletedAt(now);
        taskRepository.save(task);

        // BR-24: Đệ quy soft delete sub-tasks
        softDeleteSubtasksRecursively(projectId, currentUserId, taskId, now);
        syncWorkspaceMemberActiveTaskCounts(task.getProject(), affectedWorkspaceAssignees.toArray(UUID[]::new));

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

        ProjectStatusColumn statusColumn;
        if (request.getStatusColumnId() != null) {
            statusColumn = columnRepository.findById(request.getStatusColumnId())
                .filter(column -> column.getProject() != null && projectId.equals(column.getProject().getId()))
                .orElseThrow(() -> new NotFoundException("Column not found"));
        } else {
            statusColumn = getOrCreateDefaultColumn(parentTask.getProject());
        }
        String taskCode = taskCodeGenerator.generateTaskCode(parentTask.getProject());
        int position = (int) taskRepository.countByStatusColumnId(statusColumn.getId());
        LocalDate scheduledStart = resolveRequestedStartDate(request.getStartDate(), parentTask.getSprint(), null);
        LocalDate scheduledEnd = resolveRequestedEndDate(request.getEndDate(), request.getDueDate(), scheduledStart, null);
        validateScheduleWindow(scheduledStart, scheduledEnd);
        validateScheduledWindowWithinSprint(parentTask.getSprint(), scheduledStart, scheduledEnd);

        Task subTask = Task.builder()
            .taskCode(taskCode)
            .title(request.getTitle())
            .description(request.getDescription())
            .type(TaskType.SUB_TASK)
            .priority(parentTask.getPriority() != null ? parentTask.getPriority() : TaskPriority.MEDIUM)
            .taskStatus(statusColumn.getMappedStatus() != null ? statusColumn.getMappedStatus() : TaskStatus.TODO)
            .completedAt(statusColumn.getMappedStatus() == TaskStatus.DONE ? Instant.now() : null)
            .storyPoints(request.getStoryPoints())
            .estimatedHours(request.getEstimatedHours())
            .skillTagsRequired(request.getSkillTagsRequired())
            .startDate(scheduledStart)
            .endDate(scheduledEnd)
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
        syncWorkspaceMemberActiveTaskCount(parentTask.getProject(), assignee != null ? assignee.getId() : null);
        logActivity(projectId, currentUserId, EntityType.TASK, subTask.getId(), ActionType.SUBTASK_CREATED, null, toJson(Map.of(
                "taskCode", taskCode,
                "title", subTask.getTitle(),
                "parentTaskId", parentTask.getId()
        )));
        log.info("Sub-task created: {} under parent {}", taskCode, parentTask.getTaskCode());
        if (assignee != null && !assignee.getId().equals(currentUserId)) {
            notificationService.sendTaskAssigned(subTask, assignee, currentUser);
        }
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
    public TaskDetailResponse promoteSubTask(UUID subtaskId, PromoteSubTaskRequest request,
                                             UUID currentUserId, UUID projectId) {
        if (request == null) {
            request = new PromoteSubTaskRequest();
        }

        Task subTask = taskRepository.findById(subtaskId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + subtaskId));

        UUID resolvedProjectId = subTask.getProject().getId();
        if (projectId != null && !projectId.equals(resolvedProjectId)) {
            throw new NotFoundException("Task not found: " + subtaskId);
        }

        User currentUser = getUser(currentUserId);
        ProjectMember actorMember = projectMemberRepository
            .findByProjectIdAndUserId(resolvedProjectId, currentUserId)
            .orElse(null);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;
        if (actorMember == null && !isAdmin) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
        if (actorMember != null && actorMember.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không được promote sub-task");
        }
        if (subTask.getParentTask() == null) {
            throw new BadRequestException("Task này không phải sub-task");
        }

        boolean isMember = actorMember != null
            && actorMember.getProjectRole() == ProjectRole.MEMBER;
        boolean isPM = actorMember != null
            && actorMember.getProjectRole() == ProjectRole.PROJECT_MANAGER;
        if (!isMember && !isPM && !isAdmin) {
            throw new Forbidden("Chỉ Admin, PM hoặc Member mới được promote sub-task");
        }

        Task oldParent = subTask.getParentTask();
        UUID oldParentId = oldParent.getId();

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new BadRequestException("Tiêu đề không được để trống");
            }
            subTask.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            subTask.setDescription(request.getDescription());
        }
        if (request.getDueDate() != null) {
            subTask.setDueDate(request.getDueDate());
        }
        if (request.getAssigneeId() != null) {
            if (!projectMemberRepository.existsByProjectIdAndUserId(resolvedProjectId, request.getAssigneeId())) {
                throw new BadRequestException("Assignee không phải thành viên dự án");
            }
            subTask.setAssignee(getUser(request.getAssigneeId()));
        }

        if (subTask.getSprint() == null && oldParent.getSprint() != null) {
            subTask.setSprint(oldParent.getSprint());
        }

        subTask.setParentTask(null);
        subTask.setDepth(0);
        Task promotedTask = taskRepository.save(subTask);
        recalculateDescendantDepths(promotedTask.getId(), 0);
        taskRepository.flush();
        Task reloaded = taskRepository.findById(promotedTask.getId())
            .orElseThrow(() -> new NotFoundException("Task not found: " + promotedTask.getId()));

        String actorName = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
            ? currentUser.getFullName()
            : currentUser.getEmail();
        String auditMsg = String.format("Sub-task \"%s\" được nâng cấp thành Task bởi %s",
            reloaded.getTitle(), actorName);
        logActivity(resolvedProjectId, currentUserId, EntityType.TASK, reloaded.getId(),
            ActionType.SUBTASK_PROMOTED,
            toJson(Map.of(
                "parentTaskId", oldParentId,
                "parentTaskCode", oldParent.getTaskCode())),
            auditMsg);

        String notifTitle = "Sub-task được chuyển thành task độc lập";
        String notifBody = String.format("[%s] %s đã được tách ra từ [%s]",
            reloaded.getTaskCode(), reloaded.getTitle(), oldParent.getTaskCode());

        Set<UUID> notifiedUsers = new HashSet<>();
        projectMemberRepository.findByProjectId(resolvedProjectId).stream()
            .filter(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
            .map(ProjectMember::getUser)
            .filter(u -> !u.getId().equals(currentUserId))
            .forEach(pm -> {
                if (notifiedUsers.add(pm.getId())) {
                    notificationService.createNotification(
                        pm, NotificationType.TASK_ASSIGNED, notifTitle, notifBody,
                        EntityType.TASK.name(), reloaded.getId(),
                        resolvedProjectId, reloaded.getTaskCode(), currentUser);
                }
            });

        if (reloaded.getAssignee() != null && !reloaded.getAssignee().getId().equals(currentUserId)) {
            User assignee = reloaded.getAssignee();
            if (notifiedUsers.add(assignee.getId())) {
                notificationService.createNotification(
                    assignee, NotificationType.TASK_ASSIGNED, notifTitle, notifBody,
                    EntityType.TASK.name(), reloaded.getId(),
                    resolvedProjectId, reloaded.getTaskCode(), currentUser);
            }
        }

        webSocketService.sendToProject(resolvedProjectId.toString(), "task.subtask_promoted", Map.of(
            "promotedTaskId", reloaded.getId(),
            "previousParentTaskId", oldParentId
        ));

        reportService.invalidateOverviewCache(resolvedProjectId);

        log.info("Sub-task {} promoted to root task by {}", reloaded.getTaskCode(), currentUserId);
        return taskMapper.toDetailResponse(reloaded);
    }

    private void recalculateDescendantDepths(UUID parentTaskId, int parentDepth) {
        List<Task> children = taskRepository.findByParentTaskId(parentTaskId);
        for (Task child : children) {
            child.setDepth(parentDepth + 1);
            taskRepository.save(child);
            recalculateDescendantDepths(child.getId(), parentDepth + 1);
        }
    }

    // ════════════════════════════════════════
    // P3-BE-09: TIMELINE / GANTT VIEW
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public TimelineViewResponse getTimelineView(UUID projectId, UUID currentUserId) {
        return getTimelineView(projectId, new TaskFilterParams(), currentUserId);
    }

    @Transactional(readOnly = true)
    public TimelineViewResponse getTimelineView(UUID projectId, TaskFilterParams params, UUID currentUserId) {
        validateMembership(projectId, currentUserId);
        TaskFilterParams normalizedParams = TaskFilterSupport.resolveForQuery(params, currentUserId);
        normalizedParams.setProjectId(projectId);

        List<Task> tasks = taskRepository.findAll(TaskSpecification.buildFilter(normalizedParams, false));
        tasks = tasks.stream()
                .sorted(java.util.Comparator
                        .comparing(this::resolveTimelineStartDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(task -> resolveTimelineEndDate(task, resolveTimelineStartDate(task)),
                                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(Task::getDueDate, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparingInt(Task::getTaskPosition)
                        .thenComparing(Task::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();

        Set<UUID> visibleTaskIds = tasks.stream().map(Task::getId).collect(java.util.stream.Collectors.toSet());
        List<TaskDependency> blockerEdges = dependencyRepository.findBlockingEdgesByProjectId(projectId);
        blockerEdges = blockerEdges.stream()
                .filter(edge -> visibleTaskIds.contains(edge.getBlockingTask().getId())
                        && visibleTaskIds.contains(edge.getBlockedTask().getId()))
                .toList();

        Map<UUID, List<TimelineViewResponse.DependencyRef>> blockedByMap = new HashMap<>();
        Map<UUID, List<TimelineViewResponse.DependencyRef>> blockingMap = new HashMap<>();
        List<TimelineViewResponse.TimelineDependencyEdge> dependencies = new ArrayList<>();

        for (TaskDependency edge : blockerEdges) {
            Task blocker = edge.getBlockingTask();
            Task blocked = edge.getBlockedTask();

            blockedByMap.computeIfAbsent(blocked.getId(), unused -> new ArrayList<>())
                    .add(TimelineViewResponse.DependencyRef.builder()
                            .linkId(edge.getId())
                            .taskId(blocker.getId())
                            .taskCode(blocker.getTaskCode())
                            .title(blocker.getTitle())
                            .linkType(DependencyType.BLOCKED_BY.name())
                            .build());

            blockingMap.computeIfAbsent(blocker.getId(), unused -> new ArrayList<>())
                    .add(TimelineViewResponse.DependencyRef.builder()
                            .linkId(edge.getId())
                            .taskId(blocked.getId())
                            .taskCode(blocked.getTaskCode())
                            .title(blocked.getTitle())
                            .linkType(DependencyType.BLOCKS.name())
                            .build());

            dependencies.add(TimelineViewResponse.TimelineDependencyEdge.builder()
                    .linkId(edge.getId())
                    .linkType(edge.getLinkType().name())
                    .sourceTaskId(blocker.getId())
                    .targetTaskId(blocked.getId())
                    .blockerTaskId(blocker.getId())
                    .blockerTaskCode(blocker.getTaskCode())
                    .blockerTitle(blocker.getTitle())
                    .blockedTaskId(blocked.getId())
                    .blockedTaskCode(blocked.getTaskCode())
                    .blockedTaskTitle(blocked.getTitle())
                    .build());
        }

        List<TimelineViewResponse.TimelineTaskItem> taskItems = tasks.stream()
                .map(task -> {
                    LocalDate timelineStart = resolveTimelineStartDate(task);
                    LocalDate timelineEnd = resolveTimelineEndDate(task, timelineStart);
                    return TimelineViewResponse.TimelineTaskItem.builder()
                            .id(task.getId())
                            .taskCode(task.getTaskCode())
                            .title(task.getTitle())
                            .status(task.getTaskStatus())
                            .priority(task.getPriority())
                            .assignee(toTimelineUserSummary(task.getAssignee()))
                            .startDate(timelineStart)
                            .endDate(timelineEnd)
                            .dueDate(task.getDueDate())
                            .storyPoints(task.getStoryPoints())
                            .estimatedHours(task.getEstimatedHours())
                            .parentTaskId(task.getParentTask() != null ? task.getParentTask().getId() : null)
                            .sprint(task.getSprint() != null ? TimelineViewResponse.SprintSummary.builder()
                                    .id(task.getSprint().getId())
                                    .name(task.getSprint().getName())
                                    .status(task.getSprint().getStatus())
                                    .startDate(task.getSprint().getStartDate())
                                    .endDate(task.getSprint().getEndDate())
                                    .build() : null)
                            .blockedBy(blockedByMap.getOrDefault(task.getId(), List.of()))
                            .blocking(blockingMap.getOrDefault(task.getId(), List.of()))
                            .build();
                })
                .toList();

        return TimelineViewResponse.builder()
                .projectId(projectId)
                .totalTasks(taskItems.size())
                .totalDependencies(dependencies.size())
                .tasks(taskItems)
                .dependencies(dependencies)
                .build();
    }

    // ════════════════════════════════════════
    // P3-BE-10: CALENDAR VIEW
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public CalendarViewResponse getCalendarView(UUID projectId, int year, int month,
                                                String q, TaskStatus status, String assigneeId,
                                                UUID sprintId, List<TaskPriority> priorities, UUID currentUserId) {
        TaskFilterParams params = new TaskFilterParams();
        params.setQ(q);
        params.setStatus(status);
        params.setAssigneeId(assigneeId);
        params.setSprintId(sprintId);
        params.setPriorities(priorities);
        return getCalendarView(projectId, year, month, params, currentUserId);
    }

    @Transactional(readOnly = true)
    public CalendarViewResponse getCalendarView(UUID projectId, int year, int month,
                                                TaskFilterParams params, UUID currentUserId) {
        validateMembership(projectId, currentUserId);

        if (year < 2020 || year > 2030) {
            throw new BadRequestException("Năm không hợp lệ (2020–2030)");
        }
        if (month < 1 || month > 12) {
            throw new BadRequestException("Tháng không hợp lệ (1–12)");
        }

        LocalDate today = LocalDate.now();
        TaskFilterParams normalizedParams = TaskFilterSupport.resolveForQuery(params, currentUserId);
        normalizedParams.setProjectId(projectId);
        List<Task> tasks = taskRepository.findAll(buildCalendarSpecification(projectId, year, month, normalizedParams));
        Map<UUID, List<Task>> blockersByTaskId = buildCalendarBlockers(projectId, tasks);
        double hoursPerStoryPoint = computeHoursPerStoryPoint(projectId);

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
                    && !t.getTaskStatus().isTerminal();

            CalendarViewResponse.SprintSummary sprint = null;
            if (t.getSprint() != null) {
                sprint = CalendarViewResponse.SprintSummary.builder()
                        .id(t.getSprint().getId())
                        .name(t.getSprint().getName())
                        .status(t.getSprint().getStatus())
                        .startDate(t.getSprint().getStartDate())
                        .endDate(t.getSprint().getEndDate())
                        .build();
            }

            List<Task> blockers = blockersByTaskId.getOrDefault(t.getId(), List.of());
            boolean dependencyConflict = hasDependencyConflict(t, blockers);

            return CalendarViewResponse.CalendarTaskItem.builder()
                    .id(t.getId())
                    .taskCode(t.getTaskCode())
                    .title(t.getTitle())
                    .priority(t.getPriority())
                    .taskStatus(t.getTaskStatus())
                    .startDate(t.getStartDate())
                    .dueDate(t.getDueDate())
                    .storyPoints(t.getStoryPoints())
                    .skillTagsRequired(t.getSkillTagsRequired())
                    .columnName(t.getStatusColumn() != null ? t.getStatusColumn().getName() : null)
                    .columnColor(t.getStatusColumn() != null ? t.getStatusColumn().getColorHex() : null)
                    .isOverdue(isOverdue)
                    .assignee(assignee)
                    .sprint(sprint)
                    .dependencyConflict(dependencyConflict)
                    .blockedBy(blockers.stream().map(this::toCalendarDependencySummary).toList())
                    .build();
        }).toList();

        return CalendarViewResponse.builder()
                .year(year)
                .month(month)
                .totalTasks(items.size())
                .tasks(items)
                .workloadHeatmap(buildCalendarWorkload(items, hoursPerStoryPoint))
                .hoursPerStoryPoint(roundToOneDecimal(hoursPerStoryPoint))
                .build();
    }

    private Specification<Task> buildCalendarSpecification(UUID projectId, int year, int month, TaskFilterParams params) {
        return (root, query, cb) -> {
            query.distinct(true);

            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("project").get("id"), projectId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.isNotNull(root.get("dueDate")));
            predicates.add(cb.isNotNull(root.get("sprint")));
            predicates.add(root.get("sprint").get("status").in(SprintStatus.ACTIVE, SprintStatus.PLANNED));
            predicates.add(cb.equal(cb.function("YEAR", Integer.class, root.get("dueDate")), year));
            predicates.add(cb.equal(cb.function("MONTH", Integer.class, root.get("dueDate")), month));
            jakarta.persistence.criteria.Predicate commonPredicate = TaskSpecification.buildFilter(params, false)
                    .toPredicate(root, query, cb);
            if (commonPredicate != null) {
                predicates.add(commonPredicate);
            }

            query.orderBy(
                    cb.asc(root.get("dueDate")),
                    cb.asc(root.get("taskPosition"))
            );
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private TaskDetailResponse enrichTaskDetailResponse(Task task, UUID currentUserId) {
        UUID projectId = task.getProject().getId();
        UUID taskId = task.getId();
        TaskDetailResponse response = taskMapper.toDetailResponse(task);

        User currentUser = getUser(currentUserId);
        boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(currentUserId);
        boolean isPM = isMemberPM(projectId, currentUserId);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;

        response.setCanEdit(isAssignee || isPM || isAdmin);
        response.setCanDelete(isPM || isAdmin);
        response.setChecklistTotal((int) checklistItemRepository.countByTaskIdAndDeletedAtIsNull(taskId));
        response.setChecklistDone((int) checklistItemRepository.countByTaskIdAndIsCompletedTrueAndDeletedAtIsNull(taskId));
        response.setLinks(buildLinkSummaries(taskId));
        response.setAssigneeSuggestions(buildAssigneeSuggestions(projectId, task));
        return response;
    }

    private void validateDueDateWithinSprint(Sprint sprint, LocalDate newDueDate) {
        if (sprint == null || newDueDate == null) {
            return;
        }
        if (sprint.getStatus() != SprintStatus.ACTIVE && sprint.getStatus() != SprintStatus.PLANNED) {
            return;
        }
        boolean beforeStart = sprint.getStartDate() != null && newDueDate.isBefore(sprint.getStartDate());
        boolean afterEnd = sprint.getEndDate() != null && newDueDate.isAfter(sprint.getEndDate());
        if (beforeStart || afterEnd) {
            throw new BadRequestException("Lỗi: Hạn chót không được vượt quá phạm vi thời gian của Sprint");
        }
    }

    private void validateStartDateAgainstDependencies(Task task, LocalDate newStartDate) {
        validateStartDateAgainstDependencies(task, newStartDate, Collections.emptySet(), Collections.emptyMap());
    }

    private void validateStartDateAgainstDependencies(Task task, LocalDate newStartDate,
                                                      Set<UUID> shiftedTaskIds,
                                                      Map<UUID, LocalDate> shiftedEndDates) {
        if (newStartDate == null) {
            return;
        }
        List<Task> blockers = dependencyRepository.findBlockingTasksByBlockedTaskId(task.getId());
        for (Task blocker : blockers) {
            LocalDate blockerEnd = shiftedTaskIds.contains(blocker.getId())
                    ? shiftedEndDates.get(blocker.getId())
                    : resolveTimelineEndDate(blocker, resolveTimelineStartDate(blocker));
            if (blockerEnd != null && newStartDate.isBefore(blockerEnd)) {
                throw new BadRequestException("Lỗi: Ngày bắt đầu không được sớm hơn ngày kết thúc của task phụ thuộc");
            }
        }
    }

    private void validateScheduledWindowWithinSprint(
            Sprint sprint,
            LocalDate scheduledStart,
            LocalDate scheduledEnd
    ) {
        if (sprint == null || scheduledStart == null) {
            return;
        }
        if (sprint.getStatus() != SprintStatus.ACTIVE && sprint.getStatus() != SprintStatus.PLANNED) {
            return;
        }
        if (sprint.getStartDate() != null && scheduledStart.isBefore(sprint.getStartDate())) {
            throw new BadRequestException("Lỗi: Lịch thi công không được nằm ngoài phạm vi thời gian của Sprint");
        }
        if (scheduledEnd != null && sprint.getEndDate() != null && scheduledEnd.isAfter(sprint.getEndDate())) {
            throw new BadRequestException("Lỗi: Lịch thi công không được nằm ngoài phạm vi thời gian của Sprint");
        }
    }

    private Map<UUID, List<Task>> buildCalendarBlockers(UUID projectId, List<Task> tasks) {
        if (tasks.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> taskIds = tasks.stream().map(Task::getId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<Task>> blockersByTaskId = new HashMap<>();
        for (TaskDependency dependency : dependencyRepository.findBlockingEdgesByProjectId(projectId)) {
            Task blocked = dependency.getBlockedTask();
            if (blocked == null || !taskIds.contains(blocked.getId())) {
                continue;
            }
            blockersByTaskId
                    .computeIfAbsent(blocked.getId(), ignored -> new ArrayList<>())
                    .add(dependency.getBlockingTask());
        }
        return blockersByTaskId;
    }

    private boolean hasDependencyConflict(Task task, List<Task> blockers) {
        if (task.getStartDate() == null || blockers == null || blockers.isEmpty()) {
            return false;
        }
        return blockers.stream()
                .map(blocker -> resolveTimelineEndDate(blocker, resolveTimelineStartDate(blocker)))
                .filter(java.util.Objects::nonNull)
                .anyMatch(task.getStartDate()::isBefore);
    }

    private CalendarViewResponse.DependencySummary toCalendarDependencySummary(Task blocker) {
        return CalendarViewResponse.DependencySummary.builder()
                .taskId(blocker.getId())
                .taskCode(blocker.getTaskCode())
                .title(blocker.getTitle())
                .dueDate(blocker.getDueDate())
                .linkType(DependencyType.BLOCKS.name())
                .build();
    }

    private List<CalendarViewResponse.DayWorkload> buildCalendarWorkload(
            List<CalendarViewResponse.CalendarTaskItem> items,
            double hoursPerStoryPoint
    ) {
        Map<LocalDate, DayWorkloadAccumulator> perDay = new LinkedHashMap<>();
        for (CalendarViewResponse.CalendarTaskItem item : items) {
            if (item.getDueDate() == null || item.getAssignee() == null || item.getStoryPoints() == null || item.getStoryPoints() <= 0) {
                continue;
            }
            DayWorkloadAccumulator day = perDay.computeIfAbsent(item.getDueDate(), ignored -> new DayWorkloadAccumulator());
            day.totalStoryPoints += item.getStoryPoints();
            UserWorkloadAccumulator user = day.users.computeIfAbsent(item.getAssignee().getId(), ignored ->
                    new UserWorkloadAccumulator(item.getAssignee()));
            user.storyPoints += item.getStoryPoints();
        }

        return perDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    DayWorkloadAccumulator day = entry.getValue();
                    double totalHours = estimateHoursFromStoryPoints(day.totalStoryPoints, hoursPerStoryPoint);
                    List<CalendarViewResponse.UserWorkload> users = day.users.values().stream()
                            .sorted(Comparator.comparing((UserWorkloadAccumulator u) -> u.storyPoints).reversed())
                            .map(user -> {
                                double estimatedHours = estimateHoursFromStoryPoints(user.storyPoints, hoursPerStoryPoint);
                                return CalendarViewResponse.UserWorkload.builder()
                                        .user(user.user)
                                        .storyPoints(user.storyPoints)
                                        .estimatedHours(estimatedHours)
                                        .overloaded(estimatedHours > 8.0)
                                        .build();
                            })
                            .toList();
                    return CalendarViewResponse.DayWorkload.builder()
                            .date(entry.getKey())
                            .totalStoryPoints(day.totalStoryPoints)
                            .estimatedHours(totalHours)
                            .overloaded(totalHours > 8.0)
                            .users(users)
                            .build();
                })
                .toList();
    }

    private List<TaskDetailResponse.AssigneeSuggestion> buildAssigneeSuggestions(UUID projectId, Task task) {
        List<ProjectMember> members = projectMemberRepository.findAllByProjectIdWithUser(projectId);
        if (members.isEmpty()) {
            return List.of();
        }

        LocalDate anchorDate = task.getDueDate() != null ? task.getDueDate() : LocalDate.now();
        LocalDate weekStart = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = anchorDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        double hoursPerStoryPoint = computeHoursPerStoryPoint(projectId);
        double taskLoadHours = estimateHoursFromStoryPoints(task.getStoryPoints(), hoursPerStoryPoint);
        Map<UUID, Integer> weeklyStoryPoints = loadWeeklyStoryPoints(projectId, members, weekStart, weekEnd);

        return members.stream()
                .map(member -> {
                    List<String> effectiveSkills = resolveEffectiveSkillTags(member);
                    double similarity = computeCosineSimilarity(task.getSkillTagsRequired(), effectiveSkills);
                    double currentWeeklyLoadHours = estimateHoursFromStoryPoints(
                            weeklyStoryPoints.getOrDefault(member.getUser().getId(), 0),
                            hoursPerStoryPoint
                    );
                    double projectedWeeklyLoadHours = roundToOneDecimal(currentWeeklyLoadHours + taskLoadHours);
                    int weeklyCapacityHours = member.getUser().getWorkCapacityHours() != null
                            ? member.getUser().getWorkCapacityHours()
                            : 40;
                    return TaskDetailResponse.AssigneeSuggestion.builder()
                            .userId(member.getUser().getId())
                            .fullName(member.getUser().getFullName())
                            .avatarUrl(member.getUser().getAvatarUrl())
                            .skillTags(effectiveSkills)
                            .matchedSkills(findMatchedSkills(task.getSkillTagsRequired(), effectiveSkills))
                            .similarityScore(roundToThreeDecimals(similarity))
                            .currentWeeklyLoadHours(currentWeeklyLoadHours)
                            .projectedWeeklyLoadHours(projectedWeeklyLoadHours)
                            .weeklyCapacityHours(weeklyCapacityHours)
                            .willExceedWeeklyCapacity(projectedWeeklyLoadHours > weeklyCapacityHours)
                            .build();
                })
                .sorted(Comparator
                        .comparing(TaskDetailResponse.AssigneeSuggestion::getSimilarityScore).reversed()
                        .thenComparing(TaskDetailResponse.AssigneeSuggestion::isWillExceedWeeklyCapacity)
                        .thenComparing(TaskDetailResponse.AssigneeSuggestion::getCurrentWeeklyLoadHours)
                        .thenComparing(TaskDetailResponse.AssigneeSuggestion::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Map<UUID, Integer> loadWeeklyStoryPoints(
            UUID projectId,
            List<ProjectMember> members,
            LocalDate weekStart,
            LocalDate weekEnd
    ) {
        List<UUID> assigneeIds = members.stream().map(member -> member.getUser().getId()).toList();
        if (assigneeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> weeklyStoryPoints = new HashMap<>();
        for (Object[] row : taskRepository.sumWeeklyStoryPointsByAssignee(projectId, assigneeIds, weekStart, weekEnd)) {
            UUID assigneeId = (UUID) row[0];
            Number total = (Number) row[1];
            weeklyStoryPoints.put(assigneeId, total != null ? total.intValue() : 0);
        }
        return weeklyStoryPoints;
    }

    private double computeHoursPerStoryPoint(UUID projectId) {
        List<Sprint> completedSprints = sprintRepository.findByProject_IdAndStatusAndDeletedAtIsNullOrderByCompletedAtDesc(
                projectId,
                SprintStatus.COMPLETED,
                PageRequest.of(0, 5)
        );

        double averagePointsPerDay = completedSprints.stream()
                .filter(sprint -> sprint.getVelocity() != null && sprint.getVelocity() > 0)
                .filter(sprint -> sprint.getStartDate() != null && sprint.getEndDate() != null)
                .mapToDouble(sprint -> {
                    long days = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
                    long safeDays = Math.max(days, 1);
                    return sprint.getVelocity() / (double) safeDays;
                })
                .filter(pointsPerDay -> pointsPerDay > 0)
                .average()
                .orElse(0.0);

        if (averagePointsPerDay <= 0.0) {
            return 2.0;
        }
        return roundToOneDecimal(8.0 / averagePointsPerDay);
    }

    private int computeScheduledDurationDays(BigDecimal estimatedHours, Integer storyPoints) {
        if (estimatedHours != null && estimatedHours.compareTo(BigDecimal.ZERO) > 0) {
            return Math.max(1, estimatedHours.divide(BigDecimal.valueOf(8), 0, RoundingMode.CEILING).intValue());
        }
        if (storyPoints != null && storyPoints > 0) {
            return Math.max(1, (int) Math.ceil(storyPoints / 2.0));
        }
        return 1;
    }

    private List<String> resolveEffectiveSkillTags(ProjectMember member) {
        if (member.getSkillTags() != null && !member.getSkillTags().isEmpty()) {
            return member.getSkillTags();
        }
        if (member.getUser() != null && member.getUser().getSkillTags() != null) {
            return member.getUser().getSkillTags();
        }
        return List.of();
    }

    private List<String> findMatchedSkills(List<String> taskSkills, List<String> memberSkills) {
        if (taskSkills == null || memberSkills == null) {
            return List.of();
        }
        Set<String> normalizedMemberSkills = normalizeSkills(memberSkills);
        return taskSkills.stream()
                .filter(skill -> skill != null && normalizedMemberSkills.contains(skill.trim().toLowerCase()))
                .distinct()
                .toList();
    }

    private double computeCosineSimilarity(List<String> taskSkills, List<String> memberSkills) {
        Set<String> taskVector = normalizeSkills(taskSkills);
        Set<String> memberVector = normalizeSkills(memberSkills);
        if (taskVector.isEmpty() || memberVector.isEmpty()) {
            return 0.0;
        }
        long intersection = taskVector.stream().filter(memberVector::contains).count();
        double denominator = Math.sqrt(taskVector.size()) * Math.sqrt(memberVector.size());
        if (denominator == 0.0) {
            return 0.0;
        }
        return intersection / denominator;
    }

    private Set<String> normalizeSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String skill : skills) {
            if (skill != null && !skill.isBlank()) {
                normalized.add(skill.trim().toLowerCase());
            }
        }
        return normalized;
    }

    private double estimateHoursFromStoryPoints(Integer storyPoints, double hoursPerStoryPoint) {
        if (storyPoints == null || storyPoints <= 0) {
            return 0.0;
        }
        return roundToOneDecimal(storyPoints * hoursPerStoryPoint);
    }

    private double roundToOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundToThreeDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static class DayWorkloadAccumulator {
        private int totalStoryPoints = 0;
        private final Map<UUID, UserWorkloadAccumulator> users = new LinkedHashMap<>();
    }

    private static class UserWorkloadAccumulator {
        private final CalendarViewResponse.UserSummary user;
        private int storyPoints = 0;

        private UserWorkloadAccumulator(CalendarViewResponse.UserSummary user) {
            this.user = user;
        }
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

    private void assertAllDescendantSubtasksDone(UUID taskId) {
        List<Map<String, Object>> pendingList = collectPendingDescendantSubtasks(taskId).stream()
            .map(subTask -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", subTask.getId().toString());
                item.put("taskCode", subTask.getTaskCode());
                item.put("title", subTask.getTitle());
                item.put("taskStatus", subTask.getTaskStatus() != null ? subTask.getTaskStatus().name() : null);
                return item;
            })
            .toList();

        if (!pendingList.isEmpty()) {
            throw new com.zone.tasksphere.exception.SubtaskPendingException(pendingList);
        }
    }

    private List<Task> collectPendingDescendantSubtasks(UUID taskId) {
        List<Task> pending = new ArrayList<>();
        Deque<Task> queue = new ArrayDeque<>(taskRepository.findByParentTaskId(taskId));

        while (!queue.isEmpty()) {
            Task current = queue.removeFirst();
            if (current.getTaskStatus() != TaskStatus.DONE) {
                pending.add(current);
            }
            List<Task> children = taskRepository.findByParentTaskId(current.getId());
            if (children != null && !children.isEmpty()) {
                queue.addAll(children);
            }
        }

        return pending;
    }

    private void requireActiveSprintConfirmation(Sprint sprint, Boolean confirmActiveSprintChange) {
        if (sprint.getStatus() != SprintStatus.ACTIVE || Boolean.TRUE.equals(confirmActiveSprintChange)) {
            return;
        }
        throw new StructuredApiException(
                HttpStatus.CONFLICT,
                "SPR_ACTIVE_SCOPE_WARNING",
                "Sprint dang hoat dong. Viec them task moi se lam thay doi pham vi va sai lech burndown chart.",
                Map.of("sprintId", sprint.getId(), "sprintName", sprint.getName()));
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

    private void notifyTaskStatusStakeholders(Task task, User currentUser, TaskStatus oldStatus, TaskStatus newStatus) {
        Set<UUID> notifiedUsers = new HashSet<>();

        User assignee = task.getAssignee();
        if (assignee != null && !assignee.getId().equals(currentUser.getId()) && notifiedUsers.add(assignee.getId())) {
            notificationService.sendTaskStatusChanged(task, assignee, oldStatus.name(), newStatus.name(), currentUser);
        }

        User reporter = task.getReporter();
        if (reporter != null && !reporter.getId().equals(currentUser.getId()) && notifiedUsers.add(reporter.getId())) {
            notificationService.sendTaskStatusChanged(task, reporter, oldStatus.name(), newStatus.name(), currentUser);
        }

        if (newStatus == TaskStatus.DONE) {
            notifyDoneToPm(task, currentUser, notifiedUsers, oldStatus, newStatus);
        }
    }

    private void notifyDoneToPm(Task task, User currentUser, Set<UUID> notifiedUsers,
                                TaskStatus oldStatus, TaskStatus newStatus) {
        User pm = projectMemberRepository.findFirstByProjectIdAndProjectRoleOrderByJoinedAtAsc(
                task.getProject().getId(), ProjectRole.PROJECT_MANAGER)
                .map(ProjectMember::getUser)
                .orElse(null);

        if (pm != null && !pm.getId().equals(currentUser.getId())) {
            if (notifiedUsers.add(pm.getId())) {
                notificationService.sendTaskStatusChanged(task, pm, oldStatus.name(), newStatus.name(), currentUser);
            }
            webSocketService.sendToUser(pm.getId(), "/queue/task_done", Map.of(
                    "taskId", task.getId(),
                    "taskCode", task.getTaskCode(),
                    "title", task.getTitle(),
                    "projectId", task.getProject().getId(),
                    "completedBy", currentUser.getFullName(),
                    "completedAt", Instant.now().toString()
            ));
        }
    }

    private void enforceQaWorkflowTransition(Task task, TaskStatus oldStatus, TaskStatus newStatus,
                                             ProjectMember actorMember, User currentUser) {
        if (oldStatus == null || newStatus == null || oldStatus == newStatus) {
            return;
        }

        if (newStatus == TaskStatus.DONE && !canTransitionToDoneFrom(oldStatus)) {
            throw new BusinessRuleException("Task phải qua bước Ready for Test/Testing trước khi chuyển sang Done");
        }

        if (newStatus == TaskStatus.DONE && !canPerformTestingActions(actorMember, currentUser)) {
            throw new Forbidden("Chỉ PM/Admin hoặc thành viên có skill QA/Testing mới được chuyển task sang Done");
        }

        if (newStatus == TaskStatus.DONE
                && task != null
                && task.getAssignee() != null
                && currentUser != null
                && task.getAssignee().getId().equals(currentUser.getId())) {
            throw new Forbidden("Nghiep vu yeu cau mot Tester khac chuyen mon nghiem thu de dam bao khach quan.");
        }

        if (isQaControlledStage(oldStatus)
                && (newStatus == TaskStatus.IN_PROGRESS || newStatus == TaskStatus.TODO)
                && !canPerformTestingActions(actorMember, currentUser)) {
            throw new Forbidden("Chỉ PM/Admin hoặc thành viên có skill QA/Testing mới được trả task từ review về xử lý");
        }
    }

    private boolean canPerformTestingActions(ProjectMember actorMember, User currentUser) {
        if (currentUser != null && currentUser.getSystemRole() == SystemRole.ADMIN) {
            return true;
        }
        if (actorMember == null) {
            return false;
        }
        if (actorMember.getProjectRole() == ProjectRole.PROJECT_MANAGER) {
            return true;
        }
        return hasTestingSkill(resolveEffectiveSkillTags(actorMember));
    }

    private boolean hasTestingSkill(List<String> skills) {
        return SkillTaxonomy.hasCapability(skills, SkillTaxonomy.Capability.TESTING);
    }

    private boolean canTransitionToDoneFrom(TaskStatus status) {
        return status == TaskStatus.TESTING || status == TaskStatus.IN_REVIEW;
    }

    private boolean isQaControlledStage(TaskStatus status) {
        return status == TaskStatus.READY_FOR_TEST
                || status == TaskStatus.TESTING
                || status == TaskStatus.IN_REVIEW
                || status == TaskStatus.DONE;
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

    private void reindexTasks(List<Task> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.get(i).setTaskPosition(i);
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
        data.put("startDate", task.getStartDate());
        data.put("endDate", task.getEndDate());
        data.put("dueDate", task.getDueDate());
        data.put("storyPoints", task.getStoryPoints());
        return data;
    }

    private void collectDependentTaskIds(UUID taskId, Set<UUID> collected) {
        for (UUID dependentId : dependencyRepository.findDependentTaskIdsByTaskId(taskId)) {
            if (collected.add(dependentId)) {
                collectDependentTaskIds(dependentId, collected);
            }
        }
    }

    private LocalDate resolveRequestedStartDate(LocalDate requestedStartDate, Sprint sprint, Task existingTask) {
        if (requestedStartDate != null) {
            return requestedStartDate;
        }
        if (existingTask != null && existingTask.getStartDate() != null) {
            return existingTask.getStartDate();
        }
        if (sprint != null && sprint.getStartDate() != null) {
            return sprint.getStartDate();
        }
        if (existingTask != null && existingTask.getEndDate() != null) {
            return existingTask.getEndDate();
        }
        return null;
    }

    private LocalDate resolveRequestedEndDate(LocalDate requestedEndDate, LocalDate requestedDueDate,
                                              LocalDate resolvedStartDate, Task existingTask) {
        if (requestedEndDate != null) {
            return requestedEndDate;
        }
        if (existingTask != null && existingTask.getEndDate() != null) {
            return existingTask.getEndDate();
        }
        if (requestedDueDate != null) {
            return requestedDueDate;
        }
        if (existingTask != null && existingTask.getDueDate() != null) {
            return existingTask.getDueDate();
        }
        return resolvedStartDate;
    }

    private LocalDate resolveTimelineStartDate(Task task) {
        if (task.getStartDate() != null) {
            return task.getStartDate();
        }
        if (task.getSprint() != null && task.getSprint().getStartDate() != null) {
            return task.getSprint().getStartDate();
        }
        if (task.getEndDate() != null) {
            return task.getEndDate();
        }
        if (task.getDueDate() != null) {
            return task.getDueDate();
        }
        return LocalDate.now();
    }

    private LocalDate resolveTimelineEndDate(Task task, LocalDate resolvedStartDate) {
        if (task.getEndDate() != null) {
            return task.getEndDate();
        }
        if (task.getDueDate() != null) {
            return task.getDueDate();
        }
        return resolvedStartDate;
    }

    private void validateScheduleWindow(LocalDate scheduledStart, LocalDate scheduledEnd) {
        if (scheduledStart != null && scheduledEnd != null && scheduledEnd.isBefore(scheduledStart)) {
            throw new BadRequestException("Lỗi: Ngày kết thúc không được sớm hơn ngày bắt đầu");
        }
    }

    private void validateDependentSchedules(Task task, LocalDate nextEndDate,
                                            Set<UUID> shiftedTaskIds,
                                            boolean autoShiftDependents) {
        if (nextEndDate == null) {
            return;
        }
        List<Task> dependents = taskRepository.findAllById(dependencyRepository.findDependentTaskIdsByTaskId(task.getId()));
        for (Task dependent : dependents) {
            if (shiftedTaskIds.contains(dependent.getId())) {
                continue;
            }
            LocalDate dependentStart = resolveTimelineStartDate(dependent);
            if (dependentStart != null && dependentStart.isBefore(nextEndDate)) {
                if (autoShiftDependents) {
                    continue;
                }
                throw new BadRequestException("Cập nhật này sẽ làm dời lịch các công việc phụ thuộc. Hãy xác nhận dời dây chuyền.");
            }
        }
    }

    private void assertNoUnfinishedBlockingDependencies(UUID taskId) {
        List<Task> unfinishedBlockers = dependencyRepository.findBlockingTasksByBlockedTaskId(taskId).stream()
                .filter(blocker -> blocker.getTaskStatus() != TaskStatus.DONE
                        && blocker.getTaskStatus() != TaskStatus.CANCELLED)
                .toList();

        if (unfinishedBlockers.isEmpty()) {
            return;
        }

        List<Map<String, Object>> blockingTasks = unfinishedBlockers.stream().map(blocker -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", blocker.getId());
            item.put("taskCode", blocker.getTaskCode());
            item.put("title", blocker.getTitle());
            item.put("reason", "Task blocker chưa ở trạng thái DONE");
            return item;
        }).toList();

        throw new StructuredApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TASK_DEPENDENCY_BLOCKED",
                "Task không thể chuyển sang DONE vì còn dependency blocker chưa hoàn thành",
                Map.of("blockingTasks", blockingTasks)
        );
    }

    private TimelineViewResponse.UserSummary toTimelineUserSummary(User user) {
        if (user == null) {
            return null;
        }
        return TimelineViewResponse.UserSummary.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private Map<String, Object> mapOf(Object... kvPairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put(String.valueOf(kvPairs[i]), kvPairs[i + 1]);
        }
        return map;
    }

    private List<UUID> collectTaskTreeIds(UUID rootTaskId) {
        List<UUID> ids = new ArrayList<>();
        collectTaskTreeIds(rootTaskId, ids);
        return ids;
    }

    private void collectTaskTreeIds(UUID taskId, List<UUID> ids) {
        ids.add(taskId);
        for (Task child : taskRepository.findByParentTaskId(taskId)) {
            collectTaskTreeIds(child.getId(), ids);
        }
    }

    private Set<UUID> collectTaskTreeAssigneeIds(UUID taskId) {
        Set<UUID> assigneeIds = new HashSet<>();
        collectTaskTreeAssigneeIds(taskId, assigneeIds);
        return assigneeIds;
    }

    private void collectTaskTreeAssigneeIds(UUID taskId, Set<UUID> assigneeIds) {
        taskRepository.findById(taskId).ifPresent(task -> {
            if (task.getAssignee() != null) {
                assigneeIds.add(task.getAssignee().getId());
            }
            for (Task child : taskRepository.findByParentTaskId(taskId)) {
                collectTaskTreeAssigneeIds(child.getId(), assigneeIds);
            }
        });
    }

    private void syncWorkspaceMemberActiveTaskCount(Project project, UUID userId) {
        if (project == null || project.getWorkspace() == null || userId == null) {
            return;
        }
        UUID workspaceId = project.getWorkspace().getId();
        long exactCount = taskRepository.countAssignedOpenTasksInWorkspace(workspaceId, userId);
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .ifPresent(member -> {
                    member.setActiveTaskCount((int) exactCount);
                    workspaceMemberRepository.save(member);
                });
    }

    private void syncWorkspaceMemberActiveTaskCounts(Project project, UUID... userIds) {
        if (project == null || project.getWorkspace() == null || userIds == null) {
            return;
        }
        Set<UUID> uniqueIds = new HashSet<>();
        for (UUID userId : userIds) {
            if (userId != null) {
                uniqueIds.add(userId);
            }
        }
        uniqueIds.forEach(userId -> syncWorkspaceMemberActiveTaskCount(project, userId));
    }

    private void syncCompletedAt(Task task, TaskStatus oldStatus, TaskStatus newStatus) {
        if (newStatus == TaskStatus.DONE && oldStatus != TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
            return;
        }
        if (newStatus != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }
    }
}
