package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WebhookEvent {
    TASK_CREATED("task.created"),
    TASK_UPDATED("task.updated"),
    TASK_DELETED("task.deleted"),
    TASK_STATUS_CHANGED("task.status_changed"),
    TASK_ASSIGNED("task.assigned"),
    COMMENT_CREATED("comment.created"),
    COMMENT_UPDATED("comment.updated"),
    COMMENT_DELETED("comment.deleted"),
    SPRINT_STARTED("sprint.started"),
    SPRINT_COMPLETED("sprint.completed"),
    MEMBER_ADDED("member.added"),
    MEMBER_REMOVED("member.removed"),
    MEMBER_ROLE_CHANGED("member.role_changed"),
    PROJECT_ARCHIVED("project.archived"),
    ATTACHMENT_UPLOADED("attachment.uploaded");

    private final String eventName;
}
