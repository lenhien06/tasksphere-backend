package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.AdminDashboardResponse;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.repository.ActivityLogRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * FR-30: Admin Dashboard endpoint.
 * Response is cached in Redis for 5 minutes (configured in application.yml).
 * Cache key = "global" — all admins share the same snapshot.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "18. Admin Dashboard", description = "Thống kê tổng quan hệ thống dành cho Admin.")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final UserRepository        userRepository;
    private final ProjectRepository     projectRepository;
    private final TaskRepository        taskRepository;
    private final ActivityLogRepository activityLogRepository;

    @Operation(
        summary = "FR-30: Tổng quan hệ thống (Admin)",
        description = """
            Trả về thống kê tổng quan: users, projects, active tasks, logins/24h.
            **Cache:** Redis TTL = 5 phút.
            **Quyền:** ADMIN only.
            """
    )
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(value = "admin-dashboard", key = "'global'")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        long totalUsers     = userRepository.countAllActiveNotDeleted();
        long activeUsers    = userRepository.countByStatus(UserStatus.ACTIVE);
        long inactiveUsers  = userRepository.countByStatus(UserStatus.INACTIVE);
        long suspendedUsers = userRepository.countByStatus(UserStatus.SUSPENDED);

        long totalProjects  = projectRepository.countAllNotDeleted();
        long activeProjects = projectRepository.countActiveProjects();

        long totalActiveTasks = taskRepository.countNonTerminalTasks();

        Instant since24h    = Instant.now().minus(24, ChronoUnit.HOURS);
        long loginCount24h  = activityLogRepository.countLoginsAfter(since24h);

        AdminDashboardResponse response = AdminDashboardResponse.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .suspendedUsers(suspendedUsers)
                .totalProjects(totalProjects)
                .activeProjects(activeProjects)
                .totalActiveTasks(totalActiveTasks)
                .loginCount24h(loginCount24h)
                .generatedAt(Instant.now())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Evict cache when called by a scheduled job or after significant data changes.
     * Not exposed as an HTTP endpoint.
     */
    @CacheEvict(value = "admin-dashboard", allEntries = true)
    public void evictDashboardCache() {
        // Called programmatically if needed
    }
}
