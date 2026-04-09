package com.zone.tasksphere.dto.response;

import com.zone.tasksphere.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Verify workspace invite response")
public class VerifyWorkspaceInviteResponse {
    @Schema(description = "Workspace id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID workspaceId;

    @Schema(description = "Workspace name", example = "Engineering Team")
    private String workspaceName;

    @Schema(description = "Workspace slug", example = "engineering-team")
    private String workspaceSlug;

    @Schema(description = "Inviter name", example = "Jane Doe")
    private String inviterName;

    @Schema(description = "Invitee email", example = "user@example.com")
    private String inviteeEmail;

    @Schema(description = "Workspace role", example = "MEMBER")
    private WorkspaceRole role;

    @Schema(description = "Expires at", example = "2026-04-12T23:59:59Z")
    private Instant expiresAt;
}
