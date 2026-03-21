package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskStatus {
    TODO("To Do", "#D9D9D9", 1, false),
    IN_PROGRESS("In Progress", "#1677FF", 2, false),
    IN_REVIEW("In Review", "#FAAD14", 3, false),
    DONE("Done", "#52C41A", 4, true),
    CANCELLED("Cancelled", "#FF4D4F", 5, true);

    private final String displayName;
    private final String colorHex;
    private final int sortOrder;
    private final boolean isTerminal;

    /** BR-14: Validate trạng thái chuyển tiếp hợp lệ */
    public boolean canTransitionTo(TaskStatus next) {
        return switch (this) {
            case TODO        -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == IN_REVIEW || next == DONE;
            case IN_REVIEW   -> next == DONE || next == IN_PROGRESS;
            case DONE        -> false;
            case CANCELLED   -> false;
        };
    }
}
