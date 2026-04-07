package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.SprintStatus;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Calendar View Response")
public class CalendarViewResponse {

    @Schema(description = "Year", example = "1")
    private int year;
    @Schema(description = "Month", example = "1")
    private int month;
    @Schema(description = "Total tasks", example = "10")
    private int totalTasks;
    @Schema(description = "Tasks", example = "[]")
    private List<CalendarTaskItem> tasks;
    @Schema(description = "Per-day workload heatmap")
    private List<DayWorkload> workloadHeatmap;
    @Schema(description = "Historical conversion ratio from story points to hours", example = "2.0")
    private double hoursPerStoryPoint;

    @Data
    @Builder
    @Schema(description = "Calendar Task Item")
    public static class CalendarTaskItem {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Task code", example = "CODE-123")
        private String taskCode;
        @Schema(description = "Title", example = "Item Title")
        private String title;
        @Schema(description = "Priority", example = "HIGH")
        private TaskPriority priority;
        @Schema(description = "Task status", example = "TODO")
        private TaskStatus taskStatus;
        @Schema(description = "Start date", example = "2026-04-08")
        private LocalDate startDate;
        @Schema(description = "Due date", example = "2023-12-31")
        private LocalDate dueDate;
        @Schema(description = "Story points", example = "5")
        private Integer storyPoints;
        @Schema(description = "Task required skills")
        private List<String> skillTagsRequired;
        @Schema(description = "Status column name")
        private String columnName;
        @Schema(description = "Status column color")
        private String columnColor;
        @Schema(description = "Sprint")
        private SprintSummary sprint;
        @Schema(description = "Whether this task currently violates a blocker dependency")
        private boolean dependencyConflict;
        @Schema(description = "Blocking tasks that must finish before this task can start")
        private List<DependencySummary> blockedBy;
        @JsonProperty("isOverdue")
        @Schema(description = "Is overdue", example = "true")
        private boolean isOverdue;
        @Schema(description = "Assignee")
        private UserSummary assignee;
    }

    @Data
    @Builder
    @Schema(description = "Sprint summary")
    public static class SprintSummary {
        private UUID id;
        private String name;
        private SprintStatus status;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    @Data
    @Builder
    @Schema(description = "User Summary")
    public static class UserSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Full name", example = "John Doe")
        private String fullName;
        @Schema(description = "Avatar url", example = "https://example.com/image.png")
        private String avatarUrl;
    }

    @Data
    @Builder
    @Schema(description = "Dependency summary for calendar")
    public static class DependencySummary {
        private UUID taskId;
        private String taskCode;
        private String title;
        private LocalDate dueDate;
        private String linkType;
    }

    @Data
    @Builder
    @Schema(description = "Daily workload summary")
    public static class DayWorkload {
        private LocalDate date;
        private int totalStoryPoints;
        private double estimatedHours;
        private boolean overloaded;
        private List<UserWorkload> users;
    }

    @Data
    @Builder
    @Schema(description = "Daily workload per assignee")
    public static class UserWorkload {
        private UserSummary user;
        private int storyPoints;
        private double estimatedHours;
        private boolean overloaded;
    }
}
