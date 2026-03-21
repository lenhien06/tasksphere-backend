package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Invite Member Response")
public class InviteMemberResponse {
    @Schema(description = "Email", example = "user@example.com")
    private String email;
    @Schema(description = "Role", example = "example")
    private ProjectRole role;
    @Schema(description = "Status", example = "ACTIVE")
    private String status; // "active" or "pending"
    @Schema(description = "Is new user", example = "true")
    private boolean isNewUser;
}
