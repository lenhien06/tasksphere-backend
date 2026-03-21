package com.zone.tasksphere.dto.request;

import com.zone.tasksphere.entity.enums.SystemRole;
import jakarta.validation.constraints.NotNull;

/**
 * FR-07: Admin gán / thu hồi system role cho user.
 */
public record UpdateRolesRequest(
        @NotNull(message = "systemRole không được để trống")
        SystemRole systemRole
) {}
