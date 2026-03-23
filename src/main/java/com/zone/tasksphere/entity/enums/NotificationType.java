package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    TASK_ASSIGNED("Task Assigned", true),
    TASK_STATUS_CHANGED("Task Status Changed", false),
    TASK_COMMENTED("Task Commented", true),
    TASK_MENTIONED("Task Mentioned", true),
    TASK_DUE_SOON("Task Due Soon", true),
    TASK_OVERDUE("Task Overdue", true),
    SPRINT_STARTED("Sprint Started", true),
    SPRINT_COMPLETED("Sprint Completed", true),
    PROJECT_INVITED("Project Invited", true),
    // FIX: P5-BE-05 - Thêm các type theo SRS requirement
    PROJECT_ROLE_CHANGED("Project Role Changed", true),
    MEMBER_JOINED("Member Joined", true),
    PROJECT_MEMBER_REMOVED("Project Member Removed", true),
    PROJECT_RESTORED("Project Restored", true),
    SYSTEM_ANNOUNCEMENT("System Announcement", true);

    private final String displayName;
    private final boolean sendEmail;
}
