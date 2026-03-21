package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CustomFieldType {
    TEXT("Text"),
    NUMBER("Number"),
    DATE("Date"),
    BOOLEAN("Boolean"),
    SELECT("Select"),
    MULTI_SELECT("Multi Select"),
    URL("URL");

    private final String displayName;
}
