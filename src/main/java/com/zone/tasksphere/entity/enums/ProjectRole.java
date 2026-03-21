package com.zone.tasksphere.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectRole {
    PROJECT_MANAGER("Project Manager", 3),
    MEMBER("Member", 2),
    VIEWER("Viewer", 1);

    private final String displayName;
    private final int permissionLevel;

    public boolean canManageProject() {
        return this.permissionLevel >= 3;
    }

    public boolean canEditTask() {
        return this.permissionLevel >= 2;
    }

    @JsonCreator
    public static ProjectRole fromString(String value) {
        if (value == null) return null;
        String val = value.toUpperCase();
        if ("PM".equals(val)) return PROJECT_MANAGER;
        try {
            return ProjectRole.valueOf(val);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
