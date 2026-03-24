package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.exception.*;
import com.zone.tasksphere.mapper.TaskMapper;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.service.SprintService;
import com.zone.tasksphere.specification.TaskSpecification;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogService activityLogService;
    private final NotificationService notificationService;
    private final TaskMapper taskMapper;

    // ════════════════════════════════════════
    // MODULE 1: Sprint CRUD
    // ════════════════════════════════════════

    @Override
    public SprintDetailResponse createSprint(UUID projectId, CreateSprintRequest request, UUID currentUserId) {
        requirePM(projectId, currentUserId);
        Project project = getProject(projectId);

        // Validate dates
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
        }

        // SPR_002: name unique trong project
        if (sprintRepository.existsByProject_IdAndNameAndDeletedAtIsNull(projectId, request.getName())) {
            throw new ConflictException("Tên sprint đã tồn tại trong dự án");
        }

        // SPR_004: date overlap
        List<Sprint> overlapping = sprintRepository.findOverlappingSprints(
                projectId, request.getStartDate(), request.getEndDate());
        if (!overlapping.isEmpty()) {
            throw new BusinessRuleException("Thời gian trùng với sprint " + overlapping.get(0).getName());
        }

        Sprint sprint = Sprint.builder()
                .project(project)
                .name(request.getName())
                .goal(request.getGoal())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(SprintStatus.PLANNED)
                .build();

        sprint = sprintRepository.save(sprint);

        logActivity(projectId, currentUserId, EntityType.SPRINT, sprint.getId(),
                ActionType.CREATED, null, sprint.getName());

        log.info("Sprint created: {} in project {}", sprint.getName(), projectId);
        return toDetailResponse(sprint);
    }

    @Override
    public SprintDetailResponse updateSprint(UUID sprintId, UpdateSprintRequest request, UUID currentUserId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại"));

        requirePM(sprint.getProject().getId(), currentUserId);

        // Không cho sửa sprint đã COMPLETED
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            throw new BusinessRuleException("Không thể sửa sprint đã hoàn thành");
        }

        UUID projectId = sprint.getProject().getId();

        // Validate tên mới
        if (request.getName() != null && !request.getName().equals(sprint.getName())) {
            if (sprintRepository.existsByProject_IdAndNameAndIdNotAndDeletedAtIsNull(
                    projectId, request.getName(), sprintId)) {
                throw new ConflictException("Tên sprint đã tồn tại trong dự án");
            }
            sprint.setName(request.getName());
        }

        if (request.getGoal() != null) {
            sprint.setGoal(request.getGoal());
        }

        // Xử lý date update
        LocalDate newStart = request.getStartDate() != null ? request.getStartDate() : sprint.getStartDate();
        LocalDate newEnd = request.getEndDate() != null ? request.getEndDate() : sprint.getEndDate();

        if (request.getStartDate() != null || request.getEndDate() != null) {
            if (!newEnd.isAfter(newStart)) {
                throw new BadRequestException("Ngày kết thúc phải sau ngày bắt đầu");
            }

            // SPR_004: overlap check (exclude itself)
            List<Sprint> overlapping = sprintRepository.findOverlappingSprintsExclude(
                    projectId, newStart, newEnd, sprintId);
            if (!overlapping.isEmpty()) {
                throw new BusinessRuleException("Thời gian trùng với sprint " + overlapping.get(0).getName());
            }

            sprint.setStartDate(newStart);
            sprint.setEndDate(newEnd);
        }

        sprint = sprintRepository.save(sprint);

        logActivity(projectId, currentUserId, EntityType.SPRINT, sprint.getId(),
                ActionType.UPDATED, null, sprint.getName());

        return toDetailResponse(sprint);
    }

    @Override
    public DeleteSprintResponse deleteSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại"));

        requirePM(sprint.getProject().getId(), currentUserId);

        // Chỉ xóa được sprint PLANNED
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new BusinessRuleException("Chỉ có thể xóa sprint ở trạng thái PLANNED");
        }

        // Lấy tất cả task trong sprint → chuyển về backlog
        List<Task> tasksInSprint = sprint.getTasks();
        long tasksMoved = 0;
        if (tasksInSprint != null && !tasksInSprint.isEmpty()) {
            List<UUID> taskIds = tasksInSprint.stream()
                    .filter(t -> t.getDeletedAt() == null)
                    .map(Task::getId)
                    .toList();
            if (!taskIds.isEmpty()) {
                tasksMoved = taskRepository.batchMoveToBacklog(taskIds, sprint.getProject().getId());
            }
        }

        // Soft delete sprint
        sprint.setDeletedAt(Instant.now());
        sprintRepository.save(sprint);

        logActivity(sprint.getProject().getId(), currentUserId, EntityType.SPRINT, sprint.getId(),
                ActionType.DELETED, sprint.getName(), null);

        log.info("Sprint {} deleted, {} tasks moved to backlog", sprint.getName(), tasksMoved);
        return DeleteSprintResponse.builder()
                .message("Đã xóa sprint " + sprint.getName())
                .tasksMoved(tasksMoved)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintSummaryResponse> getSprintsByProject(UUID projectId, UUID currentUserId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }

        List<Sprint> sprints = sprintRepository.findByProject_IdAndDeletedAtIsNull(projectId);

        List<SprintSummaryResponse> result = sprints.stream().map(sprint -> {
            long taskCount = sprintRepository.countTasksBySprintId(sprint.getId());
            long doneCount = sprintRepository.countDoneTasksBySprintId(sprint.getId());
            double completionRate = taskCount > 0 ? Math.round(doneCount * 1000.0 / taskCount) / 10.0 : 0.0;

            return SprintSummaryResponse.builder()
                    .id(sprint.getId())
                    .name(sprint.getName())
                    .goal(sprint.getGoal())
                    .status(sprint.getStatus())
                    .startDate(sprint.getStartDate())
                    .endDate(sprint.getEndDate())
                    .startedAt(sprint.getStartedAt())
                    .completedAt(sprint.getCompletedAt())
                    .velocity(sprint.getVelocity())
                    .taskCount(taskCount)
                    .doneCount(doneCount)
                    .completionRate(completionRate)
                    .build();
        }).toList();

        // Sort: ACTIVE first → PLANNED by startDate → COMPLETED by completedAt DESC
        result = result.stream().sorted((a, b) -> {
            if (a.getStatus() == b.getStatus()) {
                if (a.getStatus() == SprintStatus.PLANNED) {
                    if (a.getStartDate() == null) return 1;
                    if (b.getStartDate() == null) return -1;
                    return a.getStartDate().compareTo(b.getStartDate());
                }
                if (a.getStatus() == SprintStatus.COMPLETED) {
                    if (a.getCompletedAt() == null) return 1;
                    if (b.getCompletedAt() == null) return -1;
                    return b.getCompletedAt().compareTo(a.getCompletedAt());
                }
                return 0;
            }
            return statusOrder(a.getStatus()) - statusOrder(b.getStatus());
        }).toList();

        return result;
    }

    // ════════════════════════════════════════
    // MODULE 2: Sprint Execution
    // ════════════════════════════════════════

    @Override
    public SprintStartedResponse startSprint(UUID sprintId, UUID currentUserId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại"));

        requirePM(sprint.getProject().getId(), currentUserId);

        // Sprint phải là PLANNED
        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new BusinessRuleException("Chỉ có thể bắt đầu sprint ở trạng thái PLANNED");
        }

        UUID projectId = sprint.getProject().getId();

        // SPR_003: Không được có sprint ACTIVE khác
        sprintRepository.findByProject_IdAndStatusAndDeletedAtIsNull(projectId, SprintStatus.ACTIVE)
                .ifPresent(active -> {
                    throw new BusinessRuleException(
                            "Đã có sprint đang chạy. Hoàn thành trước khi bắt đầu sprint mới.");
                });

        sprint.setStatus(SprintStatus.ACTIVE);
        sprint.setStartedAt(Instant.now());
        sprint = sprintRepository.save(sprint);

        logActivity(projectId, currentUserId, EntityType.SPRINT, sprint.getId(),
                ActionType.STATUS_CHANGED, SprintStatus.PLANNED.name(), SprintStatus.ACTIVE.name());

        // Gửi notification đến tất cả thành viên
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        final String sprintName = sprint.getName();
        final UUID sprintFinalId = sprint.getId();
        members.forEach(member -> notificationService.createNotification(
                member.getUser(),
                NotificationType.SPRINT_STARTED,
                "Sprint đã bắt đầu",
                "Sprint " + sprintName + " đã bắt đầu!",
                EntityType.SPRINT.name(), sprintFinalId
        ));

        long taskCount = sprintRepository.countTasksBySprintId(sprint.getId());

        log.info("Sprint {} started by {}", sprint.getName(), currentUserId);
        return SprintStartedResponse.builder()
                .id(sprint.getId())
                .name(sprint.getName())
                .status(sprint.getStatus().name())
                .startedAt(sprint.getStartedAt())
                .taskCount(taskCount)
                .message(sprint.getName() + " đã bắt đầu!")
                .build();
    }

    @Override
    @Transactional
    public SprintCompletedResponse completeSprint(UUID sprintId, CompleteSprintRequest request, UUID currentUserId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại"));

        requirePM(sprint.getProject().getId(), currentUserId);

        // Sprint phải đang ACTIVE
        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new BusinessRuleException("Chỉ có thể hoàn thành sprint đang ACTIVE");
        }

        UUID projectId = sprint.getProject().getId();

        // Lấy task chưa hoàn thành
        List<Task> unfinishedTasks = sprintRepository.findUnfinishedTasksBySprintId(sprintId);

        // Tính toán stats
        long totalTasks = sprintRepository.countTasksBySprintId(sprintId);
        long doneTasks = sprintRepository.countDoneTasksBySprintId(sprintId);
        long cancelledTasks = sprint.getTasks() != null
                ? sprint.getTasks().stream()
                    .filter(t -> t.getDeletedAt() == null && t.getTaskStatus() == TaskStatus.CANCELLED)
                    .count()
                : 0;

        long movedToBacklog = 0;
        String action = request.getUnfinishedTasksAction().toLowerCase().trim();

        if (!unfinishedTasks.isEmpty()) {
            List<UUID> unfinishedIds = unfinishedTasks.stream().map(Task::getId).toList();

            if ("backlog".equals(action)) {
                movedToBacklog = taskRepository.batchMoveToBacklog(unfinishedIds, projectId);
            } else if ("nextsprint".equals(action)) {
                if (request.getNextSprintId() == null) {
                    throw new BadRequestException("nextSprintId là bắt buộc khi action = nextSprint");
                }
                // Validate nextSprint là PLANNED trong project
                Sprint nextSprint = sprintRepository.findByIdAndProject_IdAndDeletedAtIsNull(
                        request.getNextSprintId(), projectId)
                        .orElseThrow(() -> new NotFoundException("Sprint tiếp theo không tồn tại hoặc không thuộc dự án"));
                if (nextSprint.getStatus() != SprintStatus.PLANNED) {
                    throw new BusinessRuleException("Sprint tiếp theo phải ở trạng thái PLANNED");
                }
                taskRepository.batchAssignToSprint(unfinishedIds, projectId, request.getNextSprintId());
            } else {
                throw new BadRequestException("unfinishedTasksAction phải là 'backlog' hoặc 'nextSprint'");
            }
        }

        // BR-22: Tính velocity = SUM storyPoints task DONE
        Integer velocity = sprintRepository.calculateVelocity(sprintId);

        sprint.setStatus(SprintStatus.COMPLETED);
        sprint.setCompletedAt(Instant.now());
        sprint.setVelocity(velocity);
        sprint = sprintRepository.save(sprint);

        logActivity(projectId, currentUserId, EntityType.SPRINT, sprint.getId(),
                ActionType.STATUS_CHANGED, SprintStatus.ACTIVE.name(), SprintStatus.COMPLETED.name());

        // Notification
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);
        final String completedSprintName = sprint.getName();
        final UUID completedSprintId = sprint.getId();
        members.forEach(member -> notificationService.createNotification(
                member.getUser(),
                NotificationType.SPRINT_COMPLETED,
                "Sprint đã hoàn thành",
                "Sprint " + completedSprintName + " đã được hoàn thành!",
                EntityType.SPRINT.name(), completedSprintId
        ));

        double completionRate = totalTasks > 0
                ? Math.round(doneTasks * 1000.0 / totalTasks) / 10.0
                : 0.0;

        log.info("Sprint {} completed with velocity={}", sprint.getName(), velocity);
        return SprintCompletedResponse.builder()
                .sprintId(sprint.getId())
                .name(sprint.getName())
                .status(sprint.getStatus().name())
                .velocity(velocity)
                .completedAt(sprint.getCompletedAt())
                .report(SprintCompletedResponse.SprintReport.builder()
                        .totalTasks(totalTasks)
                        .doneTasks(doneTasks)
                        .cancelledTasks(cancelledTasks)
                        .movedToBacklog(movedToBacklog)
                        .velocity(velocity)
                        .completionRate(completionRate)
                        .build())
                .build();
    }

    // ════════════════════════════════════════
    // MODULE 3: Backlog
    // ════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getBacklog(UUID projectId, TaskFilterParams params,
                                                  Pageable pageable, UUID currentUserId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }

        // Resolve "me"
        if ("me".equals(params.getAssigneeId())) {
            params.setAssigneeId(currentUserId.toString());
        }

        params.setProjectId(projectId);

        // Backlog = task không có sprint + các filter khác
        Specification<Task> spec = Specification
                .where(TaskSpecification.isBacklog())
                .and(TaskSpecification.buildFilter(params));

        Page<Task> page = taskRepository.findAll(spec, pageable);
        return PageResponse.fromPage(page.map(taskMapper::toResponse));
    }

    @Override
    public TaskResponse assignTaskToSprint(UUID taskId, AssignSprintRequest request, UUID currentUserId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task không tồn tại"));
        UUID oldSprintId = task.getSprint() != null ? task.getSprint().getId() : null;
        String oldSprintName = task.getSprint() != null ? task.getSprint().getName() : null;

        // FIX: BR-17 - EPIC không thể thêm vào sprint trực tiếp
        if (task.getType() == com.zone.tasksphere.entity.enums.TaskType.EPIC && request.getSprintId() != null) {
            throw new BusinessRuleException("BR-17: EPIC không thể được thêm trực tiếp vào sprint");
        }

        requirePM(task.getProject().getId(), currentUserId);

        if (request.getSprintId() == null) {
            // Chuyển về backlog
            task.setSprint(null);
        } else {
            Sprint sprint = sprintRepository.findByIdAndProject_IdAndDeletedAtIsNull(
                    request.getSprintId(), task.getProject().getId())
                    .orElseThrow(() -> new NotFoundException("Sprint không tồn tại hoặc không thuộc dự án này"));

            if (sprint.getStatus() == SprintStatus.COMPLETED) {
                throw new BusinessRuleException("Không thể thêm task vào sprint đã hoàn thành");
            }
            task.setSprint(sprint);
        }

        task = taskRepository.save(task);
        UUID newSprintId = task.getSprint() != null ? task.getSprint().getId() : null;
        String newSprintName = task.getSprint() != null ? task.getSprint().getName() : "Backlog";
        if ((oldSprintId == null && newSprintId != null)
                || (oldSprintId != null && !oldSprintId.equals(newSprintId))) {
            logActivity(task.getProject().getId(), currentUserId, EntityType.TASK, task.getId(),
                    ActionType.SPRINT_CHANGED,
                    String.format("{\"sprintId\":\"%s\",\"sprintName\":\"%s\"}", oldSprintId, oldSprintName),
                    String.format("{\"sprintId\":\"%s\",\"sprintName\":\"%s\"}", newSprintId, newSprintName));
        }
        return taskMapper.toResponse(task);
    }

    @Override
    public BatchSprintResponse batchAssignSprint(UUID projectId, BatchAssignSprintRequest request, UUID currentUserId) {
        requirePM(projectId, currentUserId);

        if (request.getSprintId() != null) {
            Sprint sprint = sprintRepository.findByIdAndProject_IdAndDeletedAtIsNull(
                    request.getSprintId(), projectId)
                    .orElseThrow(() -> new NotFoundException("Sprint không tồn tại hoặc không thuộc dự án này"));

            if (sprint.getStatus() == SprintStatus.COMPLETED) {
                throw new BusinessRuleException("Không thể thêm task vào sprint đã hoàn thành");
            }

            // Gán taskIds thuộc project (bỏ qua id lạ)
            List<UUID> validIds = request.getTaskIds();
            int updated = taskRepository.batchAssignToSprint(validIds, projectId, request.getSprintId());

            return BatchSprintResponse.builder()
                    .updatedCount(updated)
                    .failedIds(Collections.emptyList())
                    .message("Đã gán " + updated + " task vào " + sprint.getName())
                    .build();
        } else {
            // Chuyển về backlog
            List<UUID> validIds = request.getTaskIds();
            int updated = taskRepository.batchMoveToBacklog(validIds, projectId);

            return BatchSprintResponse.builder()
                    .updatedCount(updated)
                    .failedIds(Collections.emptyList())
                    .message("Đã chuyển " + updated + " task về backlog")
                    .build();
        }
    }

    // ════════════════════════════════════════
    // MODULE 4: Sprint Reports
    // ════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public BurndownResponse getBurndown(UUID sprintId, UUID currentUserId) {
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new NotFoundException("Sprint không tồn tại"));

        if (!projectMemberRepository.existsByProjectIdAndUserId(sprint.getProject().getId(), currentUserId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }

        LocalDate start = sprint.getStartDate();
        LocalDate end = sprint.getEndDate();
        if (start == null || end == null) {
            throw new BadRequestException("Sprint chưa có ngày bắt đầu/kết thúc");
        }

        // Tổng story points trong sprint
        int totalPoints = Optional.ofNullable(sprintRepository.calculateVelocity(sprintId))
                .orElse(0);
        // Tính tổng bao gồm cả task chưa DONE
        totalPoints = getTotalStoryPoints(sprintId);

        // Tính Ideal Line
        List<BurndownResponse.DataPoint> idealLine = new ArrayList<>();
        long days = end.toEpochDay() - start.toEpochDay();
        double pointsPerDay = days > 0 ? (double) totalPoints / days : 0;

        for (long i = 0; i <= days; i++) {
            LocalDate date = start.plusDays(i);
            double remaining = Math.max(0, totalPoints - (i * pointsPerDay));
            idealLine.add(BurndownResponse.DataPoint.builder()
                    .date(date)
                    .remainingPoints(Math.round(remaining * 10.0) / 10.0)
                    .build());
        }

        // Tính Actual Line từ activity_logs
        Instant startInstant = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant endInstant = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Object[]> doneByDateRaw = activityLogRepository.findDonePointsByDate(
                sprintId, startInstant, endInstant);

        // Map date → points done on that day
        Map<LocalDate, Long> donePointsByDate = new LinkedHashMap<>();
        for (Object[] row : doneByDateRaw) {
            if (row[0] != null && row[1] != null) {
                // row[0] is java.sql.Date, row[1] is Long
                LocalDate date;
                if (row[0] instanceof java.sql.Date sqlDate) {
                    date = sqlDate.toLocalDate();
                } else {
                    date = LocalDate.parse(row[0].toString());
                }
                Long points = row[1] instanceof Number n ? n.longValue() : 0L;
                donePointsByDate.put(date, points);
            }
        }

        // Build actual line: remaining = total - cumulative done
        List<BurndownResponse.DataPoint> actualLine = new ArrayList<>();
        LocalDate today = LocalDate.now();
        long cumulativeDone = 0;

        for (long i = 0; i <= days; i++) {
            LocalDate date = start.plusDays(i);
            if (date.isAfter(today)) break; // chỉ hiển thị đến hôm nay
            cumulativeDone += donePointsByDate.getOrDefault(date, 0L);
            double remaining = Math.max(0, totalPoints - cumulativeDone);
            actualLine.add(BurndownResponse.DataPoint.builder()
                    .date(date)
                    .remainingPoints(remaining)
                    .build());
        }

        return BurndownResponse.builder()
                .sprintId(sprint.getId())
                .sprintName(sprint.getName())
                .startDate(start)
                .endDate(end)
                .totalStoryPoints(totalPoints)
                .idealLine(idealLine)
                .actualLine(actualLine)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public VelocityReportResponse getVelocityReport(UUID projectId, int limit, UUID currentUserId) {
        requirePM(projectId, currentUserId);

        int safeLimit = Math.min(Math.max(limit, 1), 10);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<Sprint> completedSprints = sprintRepository
                .findByProject_IdAndStatusAndDeletedAtIsNullOrderByCompletedAtDesc(
                        projectId, SprintStatus.COMPLETED, pageable);

        List<VelocityReportResponse.SprintVelocity> sprintVelocities = completedSprints.stream()
                .map(s -> {
                    long totalTasks = sprintRepository.countTasksBySprintId(s.getId());
                    long doneTasks = sprintRepository.countDoneTasksBySprintId(s.getId());
                    return VelocityReportResponse.SprintVelocity.builder()
                            .sprintId(s.getId())
                            .sprintName(s.getName())
                            .completedAt(s.getCompletedAt() != null
                                    ? s.getCompletedAt().atZone(ZoneOffset.UTC).toLocalDate()
                                    : null)
                            .velocity(s.getVelocity() != null ? s.getVelocity() : 0)
                            .totalTasks(totalTasks)
                            .doneTasks(doneTasks)
                            .build();
                })
                .toList();

        double avgVelocity = sprintVelocities.isEmpty() ? 0 :
                sprintVelocities.stream()
                        .mapToInt(v -> v.getVelocity() != null ? v.getVelocity() : 0)
                        .average()
                        .orElse(0);
        avgVelocity = Math.round(avgVelocity * 10.0) / 10.0;

        // Trend: so sánh 2 sprint gần nhất
        String trend = "STABLE";
        if (sprintVelocities.size() >= 2) {
            int last = sprintVelocities.get(0).getVelocity() != null ? sprintVelocities.get(0).getVelocity() : 0;
            int secondLast = sprintVelocities.get(1).getVelocity() != null ? sprintVelocities.get(1).getVelocity() : 0;
            if (secondLast == 0) {
                trend = "STABLE";
            } else if (last > secondLast * 1.1) {
                trend = "UP";
            } else if (last < secondLast * 0.9) {
                trend = "DOWN";
            }
        }

        return VelocityReportResponse.builder()
                .sprints(sprintVelocities)
                .averageVelocity(avgVelocity)
                .trend(trend)
                .build();
    }

    // ════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════

    private void requirePM(UUID projectId, UUID userId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
        if (member.getProjectRole() != ProjectRole.PROJECT_MANAGER) {
            throw new Forbidden("Chỉ Project Manager mới có quyền thực hiện thao tác này");
        }
    }

    private Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));
    }

    private SprintDetailResponse toDetailResponse(Sprint sprint) {
        return SprintDetailResponse.builder()
                .id(sprint.getId())
                .name(sprint.getName())
                .goal(sprint.getGoal())
                .status(sprint.getStatus())
                .startDate(sprint.getStartDate())
                .endDate(sprint.getEndDate())
                .startedAt(sprint.getStartedAt())
                .completedAt(sprint.getCompletedAt())
                .velocity(sprint.getVelocity())
                .createdAt(sprint.getCreatedAt())
                .updatedAt(sprint.getUpdatedAt())
                .build();
    }

    private int statusOrder(SprintStatus status) {
        return switch (status) {
            case ACTIVE -> 0;
            case PLANNED -> 1;
            case COMPLETED -> 2;
            case CANCELLED -> 3;
        };
    }

    private int getTotalStoryPoints(UUID sprintId) {
        // Tổng storyPoints của tất cả task (không chỉ DONE) trong sprint
        // Dùng bằng truy vấn native thay vì viết query mới để tránh tạo thêm repo
        List<Task> tasks = sprintRepository.findUnfinishedTasksBySprintId(sprintId);
        int unfinishedPoints = tasks.stream()
                .filter(t -> t.getStoryPoints() != null)
                .mapToInt(Task::getStoryPoints)
                .sum();
        int donePoints = Optional.ofNullable(sprintRepository.calculateVelocity(sprintId)).orElse(0);
        return unfinishedPoints + donePoints;
    }

    private void logActivity(UUID projectId, UUID actorId, EntityType entityType,
                              UUID entityId, ActionType action, String oldVal, String newVal) {
        try {
            HttpServletRequest httpRequest = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, entityType, entityId,
                    action, oldVal, newVal, httpRequest);
        } catch (Exception e) {
            log.warn("Failed to log activity for sprint {}: {}", entityId, e.getMessage());
        }
    }
}
