package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Batch Assign Sprint Request")
public class BatchAssignSprintRequest {

    @NotEmpty(message = "Danh sách taskIds không được rỗng")
    @Schema(description = "Task ids", example = "550e8400-e29b-41d4-a716-446655440000")
    private List<UUID> taskIds;

    /** null = chuyển về backlog */
    @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;
}
