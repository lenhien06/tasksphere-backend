package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateWorklogRequest;
import com.zone.tasksphere.dto.request.UpdateWorklogRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.WorklogResponse;
import com.zone.tasksphere.dto.response.WorklogSummary;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.WorklogService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "8. Worklog / Time Tracking", description = "Ghi nhận thời gian làm việc trên task.")
@SecurityRequirement(name = "bearerAuth")
public class WorklogController {

    private final WorklogService worklogService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(
        summary = "Ghi thời gian làm",
        description = """
            **BR-27:** Ghi nhận thời gian làm việc cho 1 ngày.
            
            **Validate:**
            - timeSpent: 1 ≤ timeSpent ≤ 86400 giây (WRK_001)
            - logDate: KHÔNG được là ngày tương lai (WRK_002)
            
            **Auto-update:** task.actualHours = SUM(timeSpent) / 3600
            (tự động cập nhật sau mỗi worklog).
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = """
                **timeSpent** (required): Giây, 1-86400 (max 24 giờ)
                **logDate** (required): Ngày làm, format YYYY-MM-DD, không tương lai
                **note** (optional): Ghi chú, max 500 ký tự
                """
        )
    )
    @PostMapping({"/tasks/{taskId}/worklogs", "/projects/{projectId}/tasks/{taskId}/worklogs"})
    public ResponseEntity<ApiResponse<WorklogResponse>> logWork(
            @PathVariable UUID taskId,
            @PathVariable(required = false) UUID projectId,
            @Valid @RequestBody CreateWorklogRequest request) {
        WorklogResponse response = worklogService.logWork(taskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(
        summary = "Xem tổng hợp worklog",
        description = """
            Trả về summary (totalSeconds, totalFormatted) và danh sách log.
            
            **totalFormatted:** BE format sẵn thành "2h 30m" để FE hiển thị.
            Sort: logDate DESC, createdAt DESC.
            """
    )
    @GetMapping({"/tasks/{taskId}/worklogs", "/projects/{projectId}/tasks/{taskId}/worklogs"})
    public ResponseEntity<ApiResponse<WorklogSummary>> getWorklogs(
            @PathVariable UUID taskId,
            @PathVariable(required = false) UUID projectId) {
        WorklogSummary summary = worklogService.getWorklogs(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @Operation(
        summary = "Sửa worklog",
        description = """
            **BR-27 + WRK_003:** Chỉ owner/PM/Admin sửa được.
            
            Validate timeSpent và logDate như khi tạo.
            Auto-recalculate task.actualHours sau khi sửa.
            """
    )
    @PutMapping("/worklogs/{worklogId}")
    public ResponseEntity<ApiResponse<WorklogResponse>> updateWorklog(
            @PathVariable UUID worklogId,
            @Valid @RequestBody UpdateWorklogRequest request) {
        WorklogResponse response = worklogService.updateWorklog(worklogId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Xóa worklog",
        description = """
            **WRK_003:** Chỉ owner/PM/Admin xóa được.
            
            Soft delete + recalculate task.actualHours.
            """
    )
    @DeleteMapping("/worklogs/{worklogId}")
    public ResponseEntity<ApiResponse<Void>> deleteWorklog(@PathVariable UUID worklogId) {
        worklogService.deleteWorklog(worklogId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }
}
