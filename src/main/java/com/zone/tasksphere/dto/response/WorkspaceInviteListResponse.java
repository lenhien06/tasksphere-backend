package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zone.tasksphere.entity.enums.InviteStatus;
import com.zone.tasksphere.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Workspace invite list item")
public class WorkspaceInviteListResponse {
    private UUID id;
    private String email;
    private WorkspaceRole role;
    private InviteStatus status;
    private String inviterName;
    private Instant invitedAt;
    private Instant expiresAt;
    private Long daysLeft;
}
