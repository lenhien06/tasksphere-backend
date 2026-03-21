package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateWorklogRequest;
import com.zone.tasksphere.dto.request.UpdateWorklogRequest;
import com.zone.tasksphere.dto.response.WorklogResponse;
import com.zone.tasksphere.dto.response.WorklogSummary;

import java.util.UUID;

public interface WorklogService {

    WorklogResponse logWork(UUID taskId, CreateWorklogRequest request, UUID currentUserId);

    WorklogSummary getWorklogs(UUID taskId, UUID currentUserId);

    WorklogResponse updateWorklog(UUID worklogId, UpdateWorklogRequest request, UUID currentUserId);

    void deleteWorklog(UUID worklogId, UUID currentUserId);
}
