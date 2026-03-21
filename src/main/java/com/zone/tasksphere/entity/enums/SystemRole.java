package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SystemRole {
    ADMIN("Administrator", "Full system access"),
    USER("User", "Regular user with standard access");

    private final String displayName;
    private final String description;
}
