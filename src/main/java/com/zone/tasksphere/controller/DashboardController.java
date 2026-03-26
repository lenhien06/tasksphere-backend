package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.DashboardResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.DashboardService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "21. Dashboard", description = "Dashboard cá nhân sau khi đăng nhập.")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Dashboard cá nhân của user hiện tại")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<DashboardResponse>> getMyDashboard(
            @RequestParam(defaultValue = "5") Integer upcomingDays) {
        DashboardResponse response = dashboardService.getMyDashboard(getCurrentUserId(), upcomingDays);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }
}
