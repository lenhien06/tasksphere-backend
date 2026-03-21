package com.zone.tasksphere.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectVisibility {
    PRIVATE("Private"),
    INTERNAL("Internal"),
    PUBLIC("Public");

    private final String displayName;

    @JsonCreator
    public static ProjectVisibility fromString(String value) {
        if (value == null) return null;
        try {
            return ProjectVisibility.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }
}
