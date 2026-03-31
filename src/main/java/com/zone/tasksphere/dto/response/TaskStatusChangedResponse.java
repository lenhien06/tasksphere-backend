package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Task Status Changed Response")
public class TaskStatusChangedResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Task code", example = "CODE-123")
    private String taskCode;
    @Schema(description = "Old status", example = "ACTIVE")
    private TaskStatus oldStatus;
    @Schema(description = "New status", example = "ACTIVE")
    private TaskStatus newStatus;
    @Schema(description = "Updated at", example = "2023-12-31T23:59:59Z")
    private Instant updatedAt;
    @Schema(description = "New column id after status sync")
    private UUID columnId;
}
