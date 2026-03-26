package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.AddDependencyRequest;
import com.zone.tasksphere.dto.response.DependencyResponse;
import com.zone.tasksphere.dto.response.TaskDependenciesResponse;

import java.util.UUID;

public interface TaskDependencyService {

    /** POST /api/v1/tasks/{taskId}/dependencies */
    DependencyResponse addDependency(UUID projectId, UUID taskId, AddDependencyRequest request, UUID currentUserId);

    /** GET /api/v1/tasks/{taskId}/dependencies */
    TaskDependenciesResponse getDependencies(UUID projectId, UUID taskId, UUID currentUserId);

    /** DELETE /api/v1/tasks/{taskId}/dependencies/{depId} */
    void removeDependency(UUID projectId, UUID taskId, UUID depId, UUID currentUserId);
}
