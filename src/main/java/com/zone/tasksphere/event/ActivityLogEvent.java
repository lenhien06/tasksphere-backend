package com.zone.tasksphere.event;

import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ActivityLogEvent {
    private UUID projectId;
    private UUID actorId;
    private EntityType entityType;
    private UUID entityId;
    private ActionType action;
    private String oldValues;
    private String newValues;
    private String ipAddress;
    private String userAgent;
}
