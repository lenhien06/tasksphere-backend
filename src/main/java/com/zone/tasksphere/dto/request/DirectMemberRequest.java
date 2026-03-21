package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Direct Member Request")
public class DirectMemberRequest {
    @NotNull(message = "userId không được để trống")
    @Schema(description = "User id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID userId;

    @NotNull(message = "Role không được để trống")
    @Schema(description = "Role", example = "example")
    private ProjectRole role;
}
