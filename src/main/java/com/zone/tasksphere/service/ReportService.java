package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.MemberPerformanceResponse;
import com.zone.tasksphere.dto.response.ProjectOverviewResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface ReportService {

    MemberPerformanceResponse getMemberPerformance(
            UUID projectId,
            UUID sprintId,
            LocalDate dateFrom,
            LocalDate dateTo,
            UUID currentUserId);

    ProjectOverviewResponse getOverview(UUID projectId, UUID sprintId, UUID currentUserId);

    void invalidateOverviewCache(UUID projectId);
}
