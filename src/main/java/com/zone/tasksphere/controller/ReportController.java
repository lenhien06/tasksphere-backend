package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.ReportService;
import com.zone.tasksphere.service.SprintService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "17. Reports", description = "Báo cáo và biểu đồ: Burndown, Velocity, Time Tracking.")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final SprintService sprintService;
    private final ReportService reportService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(summary = "Lấy dữ liệu Burndown Chart của Sprint",
               description = "Trả về danh sách các điểm dữ liệu (ngày, lý thuyết, thực tế) để vẽ biểu đồ Burndown.")
    @GetMapping("/sprints/{sprintId}/burndown")
    public ResponseEntity<ApiResponse<BurndownResponse>> getBurndown(@PathVariable UUID sprintId) {
        BurndownResponse response = sprintService.getBurndown(sprintId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy dữ liệu Velocity Chart của dự án",
               description = "Trả về velocity (story points hoàn thành) của N sprint gần nhất.")
    @GetMapping({"/projects/{projectId}/velocity", "/projects/{projectId}/reports/velocity"})
    public ResponseEntity<ApiResponse<VelocityReportResponse>> getVelocityReport(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "5") int limit) {
        VelocityReportResponse response = sprintService.getVelocityReport(projectId, limit, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Lấy tổng quan báo cáo dự án",
        description = """
            Trả về 4 thẻ số liệu, phân bổ trạng thái (donut chart) và progress bar tổng thể.

            **Cache:** Kết quả được cache Redis 5 phút (FR-30, NFR-01).

            **Phân quyền:** PM, MEMBER, VIEWER (tất cả thành viên) đều có quyền xem.
            Project INTERNAL/PUBLIC: không cần là thành viên.

            **Lọc theo sprint:** truyền `sprintId` để giới hạn phạm vi tính toán.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Thành công"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa xác thực"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Không có quyền"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Dự án không tồn tại"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Sprint không thuộc dự án")
    })
    @GetMapping("/projects/{projectId}/reports/overview")
    public ResponseEntity<ApiResponse<ProjectOverviewResponse>> getOverview(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID sprintId) {
        ProjectOverviewResponse data = reportService.getOverview(projectId, sprintId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @Operation(summary = "Lấy báo cáo hiệu suất thành viên",
               description = "Tổng hợp hiệu suất thành viên theo dự án/sprint trong một khoảng thời gian.")
    @GetMapping({"/reports/member-performance", "/projects/{projectId}/reports/members"})
    public ResponseEntity<ApiResponse<MemberPerformanceResponse>> getMemberPerformance(
            @PathVariable(required = false) UUID projectId,
            @RequestParam(required = false) UUID projectIdParam,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        UUID resolvedProjectId = projectId != null ? projectId : projectIdParam;
        MemberPerformanceResponse response = reportService.getMemberPerformance(
                resolvedProjectId, sprintId, dateFrom, dateTo, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
