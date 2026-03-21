package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Assign Sprint Request")
public class AssignSprintRequest {
    /** null = chuyển về backlog */
    @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;
}
