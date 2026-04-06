package com.zone.tasksphere.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkspacePlan {
    FREE("Free"),
    PRO("Pro"),
    ENTERPRISE("Enterprise");

    private final String displayName;

    @JsonCreator
    public static WorkspacePlan fromString(String value) {
        if (value == null) return null;
        try {
            return WorkspacePlan.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
