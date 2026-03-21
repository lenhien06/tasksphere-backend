package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("Active", "Account is fully active"),
    INACTIVE("Inactive", "Account has been deactivated"),
    SUSPENDED("Suspended", "Account suspended by administrator"),
    PENDING_VERIFICATION("Pending Verification", "Email verification required");

    private final String displayName;
    private final String description;
}
