package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.WorkspaceHealthMetricsResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.Workspace;
import com.zone.tasksphere.entity.WorkspaceMember;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.SprintStatus;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.WorkspaceRole;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.SprintRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.WorkspaceMemberRepository;
import com.zone.tasksphere.repository.WorkspaceRepository;
import com.zone.tasksphere.service.WorkspaceHealthMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class WorkspaceHealthMetricsServiceImpl implements WorkspaceHealthMetricsService {

    private static final String CACHE_KEY = "workspace:health:%s";
    private static final long CACHE_TTL_MINUTES = 20L;
    private static final int HOURS_PER_STORY_POINT = 4;
    private static final int WEEKLY_CAPACITY_HOURS = 40;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final SprintRepository sprintRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public WorkspaceHealthMetricsResponse getHealthMetrics(UUID workspaceId, UUID requesterId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace không tồn tại"));

        if (!workspaceMemberRepository.existsByIdWorkspaceIdAndIdUserId(workspaceId, requesterId)) {
            throw new Forbidden("Bạn không phải thành viên của workspace này");
        }

        String cacheKey = keyFor(workspaceId);
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof WorkspaceHealthMetricsResponse response) {
                return response;
            }
        } catch (Exception e) {
            log.warn("[WorkspaceHealth] Redis read failed for {}: {}", workspaceId, e.getMessage());
        }

        WorkspaceHealthMetricsResponse computed = computeHealthMetrics(workspace);
        writeCache(workspaceId, computed);
        return computed;
    }

    @Override
    @Transactional(readOnly = true)
    public void refreshWorkspaceHealthMetrics(UUID workspaceId) {
        workspaceRepository.findById(workspaceId)
                .ifPresent(workspace -> writeCache(workspace.getId(), computeHealthMetrics(workspace)));
    }

    @Override
    @Transactional(readOnly = true)
    public void refreshAllWorkspaceHealthMetrics() {
        workspaceRepository.findAll().forEach(workspace -> {
            try {
                writeCache(workspace.getId(), computeHealthMetrics(workspace));
            } catch (Exception e) {
                log.warn("[WorkspaceHealth] Failed to refresh workspace {}: {}", workspace.getId(), e.getMessage());
            }
        });
    }

    private WorkspaceHealthMetricsResponse computeHealthMetrics(Workspace workspace) {
        UUID workspaceId = workspace.getId();
        Instant generatedAt = Instant.now();

        Summary summary = readSummary(workspaceId);
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        Map<UUID, WorkspaceMember> membersByUserId = members.stream()
                .collect(Collectors.toMap(member -> member.getUser().getId(), member -> member));

        List<Project> projects = projectRepository.findActiveByWorkspaceId(workspaceId);
        List<WorkspaceHealthMetricsResponse.ProjectHealthItem> projectItems = buildProjectItems(projects);
        WorkspaceHealthMetricsResponse.ProjectHealthItem focusProject = pickFocusProject(projectItems);
        int riskyProjectCount = (int) projectItems.stream()
                .filter(item -> !"HEALTHY".equals(item.getRiskLevel()))
                .count();

        List<WorkspaceHealthMetricsResponse.RiskHotspot> hotspots = taskRepository.findWorkspaceHotspots(
                        workspaceId,
                        LocalDate.now(),
                        TaskPriority.CRITICAL,
                        PageRequest.of(0, 5)
                ).stream()
                .map(this::toRiskHotspot)
                .toList();

        List<WorkspaceHealthMetricsResponse.ResourceAlert> overloadedMembers = buildResourceAlerts(
                workspaceId,
                membersByUserId
        );

        WorkspaceHealthMetricsResponse.SprintHealth sprintHealth = focusProject == null
                ? null
                : WorkspaceHealthMetricsResponse.SprintHealth.builder()
                        .sprintName(focusProject.getActiveSprintName())
                        .daysRemaining(focusProject.getDaysRemaining())
                        .totalStoryPoints(focusProject.getTotalStoryPoints())
                        .overdueTasks(focusProject.getOverdueTasks())
                        .build();

        List<WorkspaceHealthMetricsResponse.MemberPreview> memberPreview = members.stream()
                .sorted(Comparator
                        .comparingInt((WorkspaceMember member) -> roleWeight(member.getRole()))
                        .thenComparing(member -> member.getUser().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .limit(5)
                .map(member -> WorkspaceHealthMetricsResponse.MemberPreview.builder()
                        .userId(member.getUser().getId())
                        .fullName(member.getUser().getFullName())
                        .avatarUrl(member.getUser().getAvatarUrl())
                        .role(member.getRole().name())
                        .build())
                .toList();

        return WorkspaceHealthMetricsResponse.builder()
                .workspaceId(workspaceId)
                .workspaceName(workspace.getName())
                .globalProgress(summary.globalProgress())
                .overdueTaskCount(summary.overdueTaskCount())
                .riskyProjectCount(riskyProjectCount)
                .totalTaskCount(summary.totalTaskCount())
                .doneTaskCount(summary.doneTaskCount())
                .taskDistribution(WorkspaceHealthMetricsResponse.TaskDistribution.builder()
                        .todo(summary.todoTaskCount())
                        .inProgress(summary.inProgressTaskCount())
                        .done(summary.doneTaskCount())
                        .build())
                .sprintHealth(sprintHealth)
                .focusProject(focusProject == null ? null : WorkspaceHealthMetricsResponse.ProjectHighlight.builder()
                        .projectId(focusProject.getProjectId())
                        .projectName(focusProject.getProjectName())
                        .projectKey(focusProject.getProjectKey())
                        .riskLevel(focusProject.getRiskLevel())
                        .build())
                .burndown(buildBurndown(workspaceId))
                .hotspots(hotspots)
                .overloadedMembers(overloadedMembers)
                .projects(projectItems)
                .memberPreview(memberPreview)
                .generatedAt(generatedAt)
                .cachedUntil(generatedAt.plus(CACHE_TTL_MINUTES, ChronoUnit.MINUTES))
                .build();
    }

    private Summary readSummary(UUID workspaceId) {
        List<Object[]> rows = taskRepository.getWorkspaceHealthSummary(workspaceId);
        Object[] row = rows.isEmpty() || rows.get(0) == null ? new Object[0] : rows.get(0);

        int totalTasks = toInt(row, 0);
        int doneTasks = toInt(row, 1);
        int overdueTasks = toInt(row, 2);
        int totalStoryPoints = toInt(row, 3);
        int doneStoryPoints = toInt(row, 4);
        int todoTasks = toInt(row, 5);
        int inProgressTasks = toInt(row, 6);

        double globalProgress = totalStoryPoints > 0
                ? Math.round((doneStoryPoints * 10000.0) / totalStoryPoints) / 100.0
                : 0.0;

        return new Summary(totalTasks, doneTasks, overdueTasks, totalStoryPoints, doneStoryPoints, todoTasks, inProgressTasks, globalProgress);
    }

    private List<WorkspaceHealthMetricsResponse.ProjectHealthItem> buildProjectItems(List<Project> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }

        List<UUID> projectIds = projects.stream().map(Project::getId).toList();
        Map<UUID, TaskRepository.ProjectTaskStatsProjection> taskStatsByProjectId = taskRepository.getProjectTaskStats(projectIds)
                .stream()
                .collect(Collectors.toMap(TaskRepository.ProjectTaskStatsProjection::getProjectId, projection -> projection));

        Map<UUID, Sprint> activeSprintByProjectId = sprintRepository
                .findByProject_IdInAndStatusAndDeletedAtIsNull(projectIds, SprintStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(sprint -> sprint.getProject().getId(), sprint -> sprint, (left, right) ->
                        left.getEndDate() != null && right.getEndDate() != null && left.getEndDate().isBefore(right.getEndDate()) ? left : right));

        Map<UUID, Integer> activeSprintStoryPointsByProjectId = new HashMap<>();
        for (Object[] row : taskRepository.sumStoryPointsInActiveSprintsByProjectIds(projectIds)) {
            if (row.length >= 2 && row[0] instanceof UUID projectId) {
                activeSprintStoryPointsByProjectId.put(projectId, toInt(row[1]));
            }
        }

        return projects.stream()
                .map(project -> {
                    TaskRepository.ProjectTaskStatsProjection stats = taskStatsByProjectId.get(project.getId());
                    int totalTasks = stats != null ? safeLongToInt(stats.getTotal()) : 0;
                    int doneTasks = stats != null ? safeLongToInt(stats.getDone()) : 0;
                    int overdueTasks = stats != null ? safeLongToInt(stats.getOverdue()) : 0;
                    Sprint activeSprint = activeSprintByProjectId.get(project.getId());
                    Integer daysRemaining = activeSprint != null && activeSprint.getEndDate() != null
                            ? Math.max((int) ChronoUnit.DAYS.between(LocalDate.now(), activeSprint.getEndDate()), 0)
                            : null;
                    int totalStoryPoints = activeSprintStoryPointsByProjectId.getOrDefault(project.getId(), 0);
                    String riskLevel = resolveRiskLevel(overdueTasks, totalTasks, doneTasks, project.getStatus());

                    return WorkspaceHealthMetricsResponse.ProjectHealthItem.builder()
                            .projectId(project.getId())
                            .projectName(project.getName())
                            .projectKey(project.getProjectKey())
                            .status(project.getStatus().name())
                            .riskLevel(riskLevel)
                            .activeSprintName(activeSprint != null ? activeSprint.getName() : null)
                            .daysRemaining(daysRemaining)
                            .totalTasks(totalTasks)
                            .doneTasks(doneTasks)
                            .overdueTasks(overdueTasks)
                            .totalStoryPoints(totalStoryPoints)
                            .build();
                })
                .sorted(Comparator
                        .comparingInt((WorkspaceHealthMetricsResponse.ProjectHealthItem item) -> riskWeight(item.getRiskLevel()))
                        .thenComparing(WorkspaceHealthMetricsResponse.ProjectHealthItem::getOverdueTasks, Comparator.reverseOrder())
                        .thenComparing(item -> item.getDaysRemaining() == null ? Integer.MAX_VALUE : item.getDaysRemaining()))
                .toList();
    }

    private WorkspaceHealthMetricsResponse.ProjectHealthItem pickFocusProject(
            List<WorkspaceHealthMetricsResponse.ProjectHealthItem> projectItems
    ) {
        return projectItems.stream().findFirst().orElse(null);
    }

    private List<WorkspaceHealthMetricsResponse.ResourceAlert> buildResourceAlerts(
            UUID workspaceId,
            Map<UUID, WorkspaceMember> membersByUserId
    ) {
        LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<WorkspaceHealthMetricsResponse.ResourceAlert> alerts = new ArrayList<>();
        for (Object[] row : taskRepository.findWorkspaceOverloadedUsers(
                workspaceId,
                weekStart,
                weekEnd,
                HOURS_PER_STORY_POINT,
                WEEKLY_CAPACITY_HOURS
        )) {
            UUID userId = toUuid(row[0]);
            WorkspaceMember member = userId != null ? membersByUserId.get(userId) : null;
            if (member == null) {
                continue;
            }

            int allocatedHours = toInt(row[1]);
            int capacityHours = member.getUser().getWorkCapacityHours() != null
                    ? member.getUser().getWorkCapacityHours()
                    : WEEKLY_CAPACITY_HOURS;

            alerts.add(WorkspaceHealthMetricsResponse.ResourceAlert.builder()
                    .userId(member.getUser().getId())
                    .fullName(member.getUser().getFullName())
                    .avatarUrl(member.getUser().getAvatarUrl())
                    .allocatedHours(allocatedHours)
                    .capacityHours(capacityHours)
                    .overloaded(allocatedHours > capacityHours)
                    .build());
        }

        return alerts;
    }

    private List<WorkspaceHealthMetricsResponse.BurndownPoint> buildBurndown(UUID workspaceId) {
        LocalDate weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        int totalPoints = Objects.requireNonNullElse(taskRepository.sumWorkspaceActiveSprintStoryPoints(workspaceId), 0);

        Map<LocalDate, Integer> doneByDate = new LinkedHashMap<>();
        Instant from = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = weekEnd.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        for (Object[] row : taskRepository.findWorkspaceDonePointsByCompletedAt(workspaceId, from, to)) {
            LocalDate doneDate = row[0] instanceof java.sql.Date sqlDate
                    ? sqlDate.toLocalDate()
                    : (row[0] instanceof LocalDate localDate ? localDate : null);
            if (doneDate != null) {
                doneByDate.put(doneDate, toInt(row[1]));
            }
        }

        List<WorkspaceHealthMetricsResponse.BurndownPoint> points = new ArrayList<>();
        int cumulativeDone = 0;
        for (int index = 0; index < 7; index++) {
            LocalDate currentDate = weekStart.plusDays(index);
            cumulativeDone += doneByDate.getOrDefault(currentDate, 0);
            int actualRemaining = Math.max(totalPoints - cumulativeDone, 0);
            int idealRemaining = totalPoints == 0
                    ? 0
                    : Math.max((int) Math.round(totalPoints - ((totalPoints * index) / 6.0)), 0);

            points.add(WorkspaceHealthMetricsResponse.BurndownPoint.builder()
                    .label(currentDate.getDayOfWeek().name().substring(0, 3))
                    .idealRemaining(idealRemaining)
                    .actualRemaining(actualRemaining)
                    .build());
        }
        return points;
    }

    private WorkspaceHealthMetricsResponse.RiskHotspot toRiskHotspot(Task task) {
        return WorkspaceHealthMetricsResponse.RiskHotspot.builder()
                .taskId(task.getId())
                .taskCode(task.getTaskCode())
                .title(task.getTitle())
                .dueDate(task.getDueDate())
                .priority(task.getPriority() != null ? task.getPriority().name() : null)
                .overdue(task.getDueDate() != null && task.getDueDate().isBefore(LocalDate.now()))
                .projectId(task.getProject().getId())
                .projectName(task.getProject().getName())
                .assigneeId(task.getAssignee() != null ? task.getAssignee().getId() : null)
                .assigneeName(task.getAssignee() != null ? task.getAssignee().getFullName() : "Chưa phân công")
                .assigneeAvatarUrl(task.getAssignee() != null ? task.getAssignee().getAvatarUrl() : null)
                .build();
    }

    private void writeCache(UUID workspaceId, WorkspaceHealthMetricsResponse response) {
        try {
            redisTemplate.opsForValue().set(
                    keyFor(workspaceId),
                    response,
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.warn("[WorkspaceHealth] Redis write failed for {}: {}", workspaceId, e.getMessage());
        }
    }

    private String keyFor(UUID workspaceId) {
        return String.format(CACHE_KEY, workspaceId);
    }

    private String resolveRiskLevel(int overdueTasks, int totalTasks, int doneTasks, ProjectStatus status) {
        if (status == ProjectStatus.COMPLETED) {
            return "HEALTHY";
        }
        if (overdueTasks > 0) {
            return "CRITICAL";
        }
        if (totalTasks > 0 && doneTasks < totalTasks) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    private int roleWeight(WorkspaceRole role) {
        if (role == WorkspaceRole.OWNER) return 0;
        if (role == WorkspaceRole.ADMIN) return 1;
        return 2;
    }

    private int riskWeight(String riskLevel) {
        return switch (riskLevel) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private int toInt(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return 0;
        }
        return toInt(row[index]);
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private int safeLongToInt(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String string && !string.isBlank()) {
            return UUID.fromString(string);
        }
        return null;
    }

    private record Summary(
            int totalTaskCount,
            int doneTaskCount,
            int overdueTaskCount,
            int totalStoryPoints,
            int doneStoryPoints,
            int todoTaskCount,
            int inProgressTaskCount,
            double globalProgress
    ) {
    }
}
