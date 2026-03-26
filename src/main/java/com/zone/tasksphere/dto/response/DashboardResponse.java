package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponse {

    private Kpis kpis;
    private List<TaskItem> myTasks;
    private List<TaskItem> upcomingDeadlines;
    private List<RecentActivityItem> recentActivity;
    private List<ProjectSummaryItem> activeProjects;
    private boolean hasProjects;
    private boolean hasTasks;
    private int upcomingDays;
    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpis {
        private long overdueTasks;
        private long dueTodayTasks;
        private long assignedOpenTasks;
        private long completedToday;
        private long completedThisWeek;
        private long unreadNotifications;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskItem {
        private UUID id;
        private String taskCode;
        private String title;
        private UUID projectId;
        private String projectName;
        private TaskStatus status;
        private TaskPriority priority;
        private LocalDate startDate;
        private LocalDate dueDate;
        private boolean overdue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivityItem {
        private UUID id;
        private UUID projectId;
        private String projectName;
        private UUID actorId;
        private String actorName;
        private String actorAvatarUrl;
        private EntityType entityType;
        private UUID entityId;
        private ActionType action;
        private String oldValues;
        private String newValues;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectSummaryItem {
        private UUID id;
        private String name;
        private String projectKey;
        private Double progress;
        private Long taskCount;
        private Long memberCount;
        private Long overdueCount;
        private ProjectStatus status;
        private ProjectVisibility visibility;
        private String myRole;
    }
}
