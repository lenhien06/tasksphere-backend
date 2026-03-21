package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskPriority {
    LOW("Low", "#52C41A", 1),
    MEDIUM("Medium", "#FAAD14", 2),
    HIGH("High", "#FF4D4F", 3),
    CRITICAL("Critical", "#722ED1", 4);

    private final String displayName;
    private final String colorHex;
    private final int level;
}
