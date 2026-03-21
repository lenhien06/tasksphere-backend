package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ActivityLogResponse;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/activities")
@RequiredArgsConstructor
@Tag(name = "20. Activity Logs", description = "Xem nhật ký hoạt động của dự án.")
@SecurityRequirement(name = "bearerAuth")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(summary = "Lấy nhật ký hoạt động của dự án",
               description = "Trả về danh sách hoạt động có phân trang (CREATED, UPDATED, DELETED, STATUS_CHANGED, v.v.)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> getProjectActivities(
            @PathVariable UUID projectId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) EntityType type,
            @RequestParam(required = false) ActionType action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ActivityLogResponse> activities = activityLogService.getProjectActivities(
                projectId, actorId, type, action, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(activities));
    }
}
