package com.zone.tasksphere.dto.response;

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
        @Schema(description = "Due date", example = "2023-12-31")
        private LocalDate dueDate;
        @Schema(description = "Sprint")
        private SprintSummary sprint;
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
}
