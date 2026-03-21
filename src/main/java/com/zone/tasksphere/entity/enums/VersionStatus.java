package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VersionStatus {
    PLANNING("Planning"),
    IN_PROGRESS("In Progress"),
    RELEASED("Released");

    private final String displayName;
}
