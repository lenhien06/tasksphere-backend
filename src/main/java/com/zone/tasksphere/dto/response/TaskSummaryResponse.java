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

@Data
@Builder
@Schema(description = "Task Summary Response")
public class TaskSummaryResponse {
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
    @Schema(description = "Due date", example = "2023-12-31T23:59:59Z")
    private LocalDate dueDate;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
}
