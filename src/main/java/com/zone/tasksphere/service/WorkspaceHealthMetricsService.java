package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.WorkspaceHealthMetricsResponse;

import java.util.UUID;

public interface WorkspaceHealthMetricsService {

    WorkspaceHealthMetricsResponse getHealthMetrics(UUID workspaceId, UUID requesterId);

    void refreshWorkspaceHealthMetrics(UUID workspaceId);

    void refreshAllWorkspaceHealthMetrics();
}
