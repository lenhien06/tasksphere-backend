package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Assign Version Request")
public class AssignVersionRequest {
    /** null = bỏ gán version */
    @Schema(description = "Version id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID versionId;
}
