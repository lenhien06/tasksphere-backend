package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskType {
    TASK("Task", "task"),
    BUG("Bug", "bug"),
    FEATURE("Feature", "feature"),
    STORY("Story", "story"),
    EPIC("Epic", "epic"),
    SUB_TASK("Sub-task", "subtask");

    private final String displayName;
    private final String iconCode;
}
