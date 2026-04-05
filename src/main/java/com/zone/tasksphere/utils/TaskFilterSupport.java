package com.zone.tasksphere.utils;

import com.zone.tasksphere.dto.request.TaskFilterParams;

import java.util.UUID;

public final class TaskFilterSupport {

    private TaskFilterSupport() {}

    public static TaskFilterParams resolveForQuery(TaskFilterParams params, UUID currentUserId) {
        TaskFilterParams resolved = new TaskFilterParams();
        if (params == null) {
            return resolved;
        }

        resolved.setProjectId(params.getProjectId());
        resolved.setStatus(params.getStatus());
        resolved.setSprintId(params.getSprintId());
        resolved.setPriority(params.getPriority());
        resolved.setPriorities(params.getPriorities());
        resolved.setType(params.getType());
        resolved.setQ(params.getQ());
        resolved.setOverdue(params.getOverdue());
        resolved.setDueSoon(params.getDueSoon());

        String assigneeId = params.getAssigneeId();
        if (assigneeId != null) {
            String trimmed = assigneeId.trim();
            if (trimmed.isEmpty()) {
                resolved.setAssigneeId(null);
            } else if ("me".equalsIgnoreCase(trimmed) && currentUserId != null) {
                resolved.setAssigneeId(currentUserId.toString());
            } else {
                resolved.setAssigneeId(trimmed);
            }
        }

        return resolved;
    }
}
