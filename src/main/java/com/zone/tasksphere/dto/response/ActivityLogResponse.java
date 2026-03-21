package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Activity Log Response")
public class ActivityLogResponse {
    
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Project id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID projectId;

    @Schema(description = "Actor id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID actorId;

    @Schema(description = "Actor name", example = "John Doe")
    private String actorName;

    @Schema(description = "Actor avatar", example = "https://example.com/image.png")
    private String actorAvatar;

    @Schema(description = "Entity type", example = "TASK")
    private EntityType entityType;

    @Schema(description = "Entity id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID entityId;

    @Schema(description = "Action", example = "example")
    private ActionType action;

    @Schema(description = "Old values", example = "string")
    private String oldValues;

    @Schema(description = "New values", example = "string")
    private String newValues;

    @Schema(description = "Ip address", example = "string")
    private String ipAddress;

    @Schema(description = "User agent", example = "string")
    private String userAgent;

    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
}
