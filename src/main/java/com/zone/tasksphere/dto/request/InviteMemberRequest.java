package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Invite Member Request")
public class InviteMemberRequest {
    @Email(message = "Email không hợp lệ")
    @NotNull(message = "Email không được để trống")
    @Schema(description = "Email", example = "user@example.com")
    private String email;

    @NotNull(message = "Role không được để trống")
    @Schema(description = "Role", example = "example")
    private ProjectRole role;
}
