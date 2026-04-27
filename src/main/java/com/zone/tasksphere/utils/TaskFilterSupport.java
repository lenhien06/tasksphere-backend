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
        resolved.setQ(FilterSanitizer.sanitizeQ(params.getQ()));
        resolved.setOverdue(params.getOverdue());
        resolved.setDueSoon(params.getDueSoon());
        resolved.setActiveSprintOnly(params.getActiveSprintOnly());

        String sanitized = FilterSanitizer.sanitizeAssigneeId(params.getAssigneeId());
        if ("me".equalsIgnoreCase(sanitized) && currentUserId != null) {
            resolved.setAssigneeId(currentUserId.toString());
        } else {
            resolved.setAssigneeId(sanitized);
        }

        return resolved;
    }
}
