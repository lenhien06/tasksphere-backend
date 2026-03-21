package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zone.tasksphere.entity.enums.InviteStatus;
import com.zone.tasksphere.entity.enums.ProjectRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Project Invite Response")
public class ProjectInviteResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Email", example = "user@example.com")
    private String email;
    @Schema(description = "Role", example = "example")
    private ProjectRole role;
    @Schema(description = "Status", example = "ACTIVE")
    private InviteStatus status;
    @Schema(description = "Inviter name", example = "John Doe")
    private String inviterName;
    @Schema(description = "Invited at", example = "2023-12-31T23:59:59Z")
    private Instant invitedAt;
    @Schema(description = "Expires at", example = "2023-12-31T23:59:59Z")
    private Instant expiresAt;
    @Schema(description = "Days left", example = "1")
    private Long daysLeft;       // null khi status != PENDING
    // Fields for GET /users/me/invites
    @Schema(description = "Project id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID projectId;
    @Schema(description = "Project name", example = "John Doe")
    private String projectName;
    @Schema(description = "Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;
}