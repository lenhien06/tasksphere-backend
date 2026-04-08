package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskStatus {
    TODO("To Do", "#D9D9D9", 1, false),
    IN_PROGRESS("In Progress", "#1677FF", 2, false),
    READY_FOR_TEST("Ready for Test", "#FAAD14", 3, false),
    TESTING("Testing", "#722ED1", 4, false),
    IN_REVIEW("In Review", "#FAAD14", 5, false),
    DONE("Done", "#52C41A", 6, true),
    CANCELLED("Cancelled", "#FF4D4F", 7, true);

    private final String displayName;
    private final String colorHex;
    private final int sortOrder;
    private final boolean isTerminal;

    public boolean canTransitionTo(TaskStatus next) {
        // Open transition model (Jira-like): allow moving between any statuses.
        return next != null;
    }
}
