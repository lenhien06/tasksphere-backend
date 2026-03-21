package com.zone.tasksphere.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProjectStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
    DELETED;

    @JsonCreator
    public static ProjectStatus fromString(String value) {
        if (value == null) return null;
        try {
            return ProjectStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
