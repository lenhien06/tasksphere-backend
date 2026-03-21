package com.zone.tasksphere.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * FR-30: Admin dashboard summary response.
 * Cached in Redis for 5 minutes (NFR-30).
 */
@Data
@Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long activeUsers;
    private long inactiveUsers;
    private long suspendedUsers;

    private long totalProjects;
    private long activeProjects;

    private long totalActiveTasks;

    private long loginCount24h;

    private Instant generatedAt;
}
