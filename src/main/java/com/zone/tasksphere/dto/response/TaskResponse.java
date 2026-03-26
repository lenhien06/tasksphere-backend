package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Light-weight response — dùng cho Kanban board và danh sách task */
@Data
@Builder
@Schema(description = "Task Response")
public class TaskResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Task code", example = "CODE-123")
    private String taskCode;
    @Schema(description = "Title", example = "Item Title")
    private String title;
    @Schema(description = "Type", example = "TASK")
    private TaskType type;
    @Schema(description = "Priority", example = "HIGH")
    private TaskPriority priority;
    @Schema(description = "Task status", example = "ACTIVE")
    private TaskStatus taskStatus;

    @Schema(description = "Sprint id — null nếu task ở Backlog", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;
    @Schema(description = "Sprint name — null nếu task ở Backlog", example = "Sprint 4 — Polish & Release")
    private String sprintName;

    @Schema(description = "Column id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID columnId;
    @Schema(description = "Column name", example = "John Doe")
    private String columnName;
    @Schema(description = "Task position", example = "1")
    private int taskPosition;

    @Schema(description = "Story points", example = "1")
    private Integer storyPoints;
    @Schema(description = "Start date", example = "2023-12-01")
    private LocalDate startDate;
    @Schema(description = "Due date", example = "2023-12-31T23:59:59Z")
    private LocalDate dueDate;
    @Schema(description = "Overdue", example = "true")
    private boolean overdue;

    @Schema(description = "Subtask count", example = "10")
    private int subtaskCount;
    @Schema(description = "Subtask done", example = "1")
    private int subtaskDone;
    @Schema(description = "Comments count", example = "10")
    private int commentsCount;
    @Schema(description = "Attachments count", example = "10")
    private int attachmentsCount;

    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
    @Schema(description = "Updated at", example = "2023-12-31T23:59:59Z")
    private Instant updatedAt;

    @Schema(description = "Assignee", example = "example")
    private UserSummary assignee;
    @Schema(description = "Reporter", example = "example")
    private UserSummary reporter;

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
