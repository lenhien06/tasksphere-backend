package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.*;

import java.util.List;
import java.util.UUID;

public interface SprintService {

    // ── Module 1: Sprint CRUD ────────────────────────────────────────
    SprintDetailResponse createSprint(UUID projectId, CreateSprintRequest request, UUID currentUserId);

    SprintDetailResponse updateSprint(UUID sprintId, UpdateSprintRequest request, UUID currentUserId);

    DeleteSprintResponse deleteSprint(UUID sprintId, UUID currentUserId);

    List<SprintSummaryResponse> getSprintsByProject(UUID projectId, UUID currentUserId);

    // ── Module 2: Sprint Execution ───────────────────────────────────
    SprintStartedResponse startSprint(UUID sprintId, UUID currentUserId);

    SprintCompletedResponse completeSprint(UUID sprintId, CompleteSprintRequest request, UUID currentUserId);

    // ── Module 3: Backlog ────────────────────────────────────────────
    PageResponse<TaskResponse> getBacklog(UUID projectId, TaskFilterParams params,
                                          org.springframework.data.domain.Pageable pageable,
                                          UUID currentUserId);

    TaskResponse assignTaskToSprint(UUID taskId, AssignSprintRequest request, UUID currentUserId);

    BatchSprintResponse batchAssignSprint(UUID projectId, BatchAssignSprintRequest request, UUID currentUserId);

    // ── Module 4: Sprint Reports ─────────────────────────────────────
    BurndownResponse getBurndown(UUID sprintId, UUID currentUserId);

    VelocityReportResponse getVelocityReport(UUID projectId, int limit, UUID currentUserId);
}
