package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update Role Request")
public class UpdateRoleRequest {
    @NotNull(message = "Role mới không được để trống")
    @Schema(description = "Role", example = "example")
    private ProjectRole role;
}
