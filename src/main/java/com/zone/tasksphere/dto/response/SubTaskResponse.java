package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Sub Task Response")
public class SubTaskResponse {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Task code", example = "CODE-123")
    private String taskCode;
    @Schema(description = "Title", example = "Item Title")
    private String title;
    @Schema(description = "Task status", example = "ACTIVE")
    private TaskStatus taskStatus;
    @Schema(description = "Priority", example = "HIGH")
    private TaskPriority priority;
    @Schema(description = "Assignee", example = "example")
    private UserSummary assignee;
    @Schema(description = "Due date", example = "2023-12-31T23:59:59Z")
    private LocalDate dueDate;
    @Schema(description = "Depth", example = "1")
    private int depth;
    @Schema(description = "Subtask count", example = "10")
    private int subtaskCount;
    @Schema(description = "Completed subtask count", example = "3")
    private int completedSubtaskCount;

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
