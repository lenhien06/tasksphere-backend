package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.DashboardResponse;
import com.zone.tasksphere.entity.ActivityLog;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.repository.ActivityLogRepository;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.service.DashboardService;
import com.zone.tasksphere.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final int DEFAULT_UPCOMING_DAYS = 5;
    private static final int MIN_UPCOMING_DAYS = 1;
    private static final int MAX_UPCOMING_DAYS = 14;
    private static final int DEFAULT_MY_TASK_LIMIT = 8;
    private static final int DEFAULT_UPCOMING_LIMIT = 8;
    private static final int DEFAULT_ACTIVITY_LIMIT = 10;
    private static final int DEFAULT_PROJECT_LIMIT = 6;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final NotificationService notificationService;

    @Override
    public DashboardResponse getMyDashboard(UUID currentUserId, Integer upcomingDays) {
        int resolvedUpcomingDays = normalizeUpcomingDays(upcomingDays);
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        List<Project> workspaceProjects = projectRepository.findOwnedOrMemberProjects(currentUserId);
        List<Project> activeProjects = workspaceProjects.stream()
                .filter(project -> project.getStatus() == ProjectStatus.ACTIVE)
                .limit(DEFAULT_PROJECT_LIMIT)
                .toList();

        List<UUID> workspaceProjectIds = workspaceProjects.stream().map(Project::getId).toList();
        List<UUID> activeProjectIds = activeProjects.stream().map(Project::getId).toList();
        List<Task> myOpenTasks = taskRepository.findAssignedOpenTasksForDashboard(
                currentUserId, PageRequest.of(0, DEFAULT_MY_TASK_LIMIT));
        List<Task> upcomingTasks = taskRepository.findUpcomingAssignedTasksForDashboard(
                currentUserId, today, today.plusDays(resolvedUpcomingDays), PageRequest.of(0, DEFAULT_UPCOMING_LIMIT));

        long overdueTasks = taskRepository.countOverdueAssignedTasks(currentUserId, today);
        long dueTodayTasks = taskRepository.countDueTodayAssignedTasks(currentUserId, today);
        long assignedOpenTasks = taskRepository.countAssignedOpenTasks(currentUserId);
        long completedToday = taskRepository.countCompletedAssignedTasksBetween(
                currentUserId,
                today.atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        long completedThisWeek = taskRepository.countCompletedAssignedTasksBetween(
                currentUserId,
                startOfWeek.atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
        long unreadNotifications = notificationService.getUnreadCount(currentUserId);

        boolean hasProjects = !workspaceProjects.isEmpty();
        boolean hasTasks = taskRepository.existsWorkspaceTasks(currentUserId);

        Map<UUID, Long> memberCountMap = activeProjectIds.isEmpty()
                ? Map.of()
                : projectMemberRepository.getProjectMemberCounts(activeProjectIds).stream().collect(Collectors.toMap(
                        ProjectMemberRepository.ProjectMemberCountProjection::getProjectId,
                        ProjectMemberRepository.ProjectMemberCountProjection::getMemberCount
                ));
        Map<UUID, TaskRepository.ProjectTaskStatsProjection> taskStatsMap = activeProjectIds.isEmpty()
                ? Map.of()
                : taskRepository.getProjectTaskStats(activeProjectIds).stream().collect(Collectors.toMap(
                        TaskRepository.ProjectTaskStatsProjection::getProjectId,
                        Function.identity()
                ));

        Map<UUID, ProjectRole> roleMap = activeProjectIds.isEmpty()
                ? Map.of()
                : projectMemberRepository.findByUserIdAndProjectIdIn(currentUserId, activeProjectIds).stream()
                        .collect(Collectors.toMap(
                                pm -> pm.getProject().getId(),
                                ProjectMember::getProjectRole
                        ));

        Map<UUID, Project> projectMap = workspaceProjects.stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));

        List<ActivityLog> recentLogs = workspaceProjectIds.isEmpty()
                ? List.of()
                : activityLogRepository.findRecentByProjectIds(workspaceProjectIds, PageRequest.of(0, DEFAULT_ACTIVITY_LIMIT));

        return DashboardResponse.builder()
                .kpis(DashboardResponse.Kpis.builder()
                        .overdueTasks(overdueTasks)
                        .dueTodayTasks(dueTodayTasks)
                        .assignedOpenTasks(assignedOpenTasks)
                        .completedToday(completedToday)
                        .completedThisWeek(completedThisWeek)
                        .unreadNotifications(unreadNotifications)
                        .build())
                .myTasks(myOpenTasks.stream().map(this::toTaskItem).toList())
                .upcomingDeadlines(upcomingTasks.stream().map(this::toTaskItem).toList())
                .recentActivity(recentLogs.stream()
                        .filter(log -> projectMap.containsKey(log.getProjectId()))
                        .map(log -> toRecentActivityItem(log, projectMap.get(log.getProjectId())))
                        .toList())
                .activeProjects(activeProjects.stream()
                        .map(project -> toProjectSummary(project, taskStatsMap.get(project.getId()),
                                memberCountMap.getOrDefault(project.getId(), 0L), roleMap.get(project.getId()), currentUserId))
                        .toList())
                .hasProjects(hasProjects)
                .hasTasks(hasTasks)
                .upcomingDays(resolvedUpcomingDays)
                .generatedAt(now)
                .build();
    }

    private int normalizeUpcomingDays(Integer upcomingDays) {
        int resolved = upcomingDays == null ? DEFAULT_UPCOMING_DAYS : upcomingDays;
        if (resolved < MIN_UPCOMING_DAYS || resolved > MAX_UPCOMING_DAYS) {
            throw new BadRequestException("upcomingDays phải nằm trong khoảng 1-14");
        }
        return resolved;
    }

    private DashboardResponse.TaskItem toTaskItem(Task task) {
        return DashboardResponse.TaskItem.builder()
                .id(task.getId())
                .taskCode(task.getTaskCode())
                .title(task.getTitle())
                .projectId(task.getProject().getId())
                .projectName(task.getProject().getName())
                .status(task.getTaskStatus())
                .priority(task.getPriority())
                .startDate(task.getStartDate())
                .dueDate(task.getDueDate())
                .overdue(task.getDueDate() != null
                        && task.getDueDate().isBefore(LocalDate.now())
                        && task.getTaskStatus() != TaskStatus.DONE
                        && task.getTaskStatus() != TaskStatus.CANCELLED)
                .build();
    }

    private DashboardResponse.RecentActivityItem toRecentActivityItem(ActivityLog log, Project project) {
        return DashboardResponse.RecentActivityItem.builder()
                .id(log.getId())
                .projectId(log.getProjectId())
                .projectName(project != null ? project.getName() : null)
                .actorId(log.getActor() != null ? log.getActor().getId() : null)
                .actorName(log.getActor() != null ? log.getActor().getFullName() : "System")
                .actorAvatarUrl(log.getActor() != null ? log.getActor().getAvatarUrl() : null)
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .oldValues(log.getOldValues())
                .newValues(log.getNewValues())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private DashboardResponse.ProjectSummaryItem toProjectSummary(Project project,
                                                                 TaskRepository.ProjectTaskStatsProjection stats,
                                                                 long memberCount,
                                                                 ProjectRole role,
                                                                 UUID currentUserId) {
        long totalTasks = stats != null && stats.getTotal() != null ? stats.getTotal() : 0L;
        long doneTasks = stats != null && stats.getDone() != null ? stats.getDone() : 0L;
        long overdueTasks = stats != null && stats.getOverdue() != null ? stats.getOverdue() : 0L;
        double progress = totalTasks > 0 ? Math.round((doneTasks * 1000.0 / totalTasks)) / 10.0 : 0.0;
        String myRole = project.getOwner() != null && project.getOwner().getId().equals(currentUserId)
                ? ProjectRole.PROJECT_MANAGER.name()
                : role != null ? role.name() : null;

        return DashboardResponse.ProjectSummaryItem.builder()
                .id(project.getId())
                .name(project.getName())
                .projectKey(project.getProjectKey())
                .progress(progress)
                .taskCount(totalTasks)
                .memberCount(memberCount)
                .overdueCount(overdueTasks)
                .status(project.getStatus())
                .visibility(project.getVisibility())
                .myRole(myRole)
                .build();
    }
}
