package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecurringFrequency {
    DAILY("Daily", "0 0 * * *"),
    WEEKLY("Weekly", "0 0 * * 1"),
    MONTHLY("Monthly", "0 0 1 * *"),
    YEARLY("Yearly", "0 0 1 1 *"),
    CUSTOM("Custom", "");

    private final String displayName;
    private final String cronExpression;
}
