package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.AssignVersionRequest;
import com.zone.tasksphere.dto.request.CreateVersionRequest;
import com.zone.tasksphere.dto.request.UpdateVersionRequest;
import com.zone.tasksphere.dto.response.DeleteVersionResponse;
import com.zone.tasksphere.dto.response.TaskResponse;
import com.zone.tasksphere.dto.response.VersionResponse;

import java.util.List;
import java.util.UUID;

public interface VersionService {

    VersionResponse createVersion(UUID projectId, CreateVersionRequest request, UUID currentUserId);

    List<VersionResponse> getVersionsByProject(UUID projectId, UUID currentUserId);

    VersionResponse updateVersion(UUID versionId, UpdateVersionRequest request, UUID currentUserId);

    DeleteVersionResponse deleteVersion(UUID versionId, UUID currentUserId);

    TaskResponse assignTaskVersion(UUID taskId, AssignVersionRequest request, UUID currentUserId);
}
