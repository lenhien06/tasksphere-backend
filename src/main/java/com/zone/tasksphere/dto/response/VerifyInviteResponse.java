package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Verify Invite Response")
public class VerifyInviteResponse {
    @Schema(description = "Project id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID projectId;
    @Schema(description = "Project name", example = "John Doe")
    private String projectName;
    @Schema(description = "Inviter name", example = "John Doe")
    private String inviterName;
    @Schema(description = "Invitee email", example = "user@example.com")
    private String inviteeEmail;
    @Schema(description = "Role", example = "example")
    private ProjectRole role;
    @Schema(description = "Expires at", example = "2023-12-31T23:59:59Z")
    private Instant expiresAt;
}
