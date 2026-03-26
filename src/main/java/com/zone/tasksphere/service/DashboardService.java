package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.DashboardResponse;

import java.util.UUID;

public interface DashboardService {

    DashboardResponse getMyDashboard(UUID currentUserId, Integer upcomingDays);
}
