package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.SetRecurrenceRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.RecurrenceResponse;
import com.zone.tasksphere.dto.response.TaskSummaryResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.RecurringTaskService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "15. Recurring Tasks", description = "Tự động tạo task lặp lại (hàng ngày, hàng tuần, hàng tháng).")
@SecurityRequirement(name = "bearerAuth")
public class RecurringTaskController {

    private final RecurringTaskService recurringTaskService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(summary = "Thiết lập lặp lại cho task")
    @PostMapping("/tasks/{taskId}/recurrence")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> setRecurrence(
            @PathVariable UUID taskId,
            @Valid @RequestBody SetRecurrenceRequest request) {
        RecurrenceResponse response = recurringTaskService.setRecurrence(taskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy thông tin lặp lại của task")
    @GetMapping("/tasks/{taskId}/recurrence")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> getRecurrence(@PathVariable UUID taskId) {
        RecurrenceResponse response = recurringTaskService.getRecurrence(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật thiết lập lặp lại")
    @PutMapping("/tasks/{taskId}/recurrence")
    public ResponseEntity<ApiResponse<RecurrenceResponse>> updateRecurrence(
            @PathVariable UUID taskId,
            @Valid @RequestBody SetRecurrenceRequest request) {
        RecurrenceResponse response = recurringTaskService.updateRecurrence(taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Hủy bỏ thiết lập lặp lại")
    @DeleteMapping("/tasks/{taskId}/recurrence")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteRecurrence(@PathVariable UUID taskId) {
        Map<String, Object> result = recurringTaskService.deleteRecurrence(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Lấy danh sách các instance đã được tạo từ task mẫu")
    @GetMapping("/tasks/{taskId}/recurrence/instances")
    public ResponseEntity<ApiResponse<List<TaskSummaryResponse>>> getInstances(@PathVariable UUID taskId) {
        List<TaskSummaryResponse> response = recurringTaskService.getInstances(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
