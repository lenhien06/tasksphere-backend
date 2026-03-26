package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.AddDependencyRequest;
import com.zone.tasksphere.dto.request.CreateTaskRequest;
import com.zone.tasksphere.dto.request.PromoteSubTaskRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.DependencyResponse;
import com.zone.tasksphere.dto.response.SubTaskResponse;
import com.zone.tasksphere.dto.response.TaskDetailResponse;
import com.zone.tasksphere.dto.response.TaskDependenciesResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.TaskDependencyService;
import com.zone.tasksphere.service.TaskService;
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
import java.util.UUID;

/**
 * Shortcut endpoints at /api/v1/tasks/{taskId}/...
 * Dùng khi FE không có projectId trong context (ví dụ: task detail page).
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "4. Task Management", description = "CRUD task, Kanban board, Sub-task, filter, calendar view.")
@SecurityRequirement(name = "bearerAuth")
public class TaskShortcutController {

    private final TaskService taskService;
    private final TaskDependencyService taskDependencyService;

    @Operation(summary = "Tạo sub-task (shortcut, trả SubTaskResponse cho FE task detail)")
    @PostMapping("/{parentTaskId}/subtasks")
    public ResponseEntity<ApiResponse<SubTaskResponse>> createSubTask(
            @PathVariable UUID parentTaskId,
            @Valid @RequestBody CreateTaskRequest request) {
        SubTaskResponse response = taskService.createSubTaskLight(parentTaskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách sub-tasks (shortcut, không cần projectId trong path)")
    @GetMapping("/{taskId}/subtasks")
    public ResponseEntity<ApiResponse<List<SubTaskResponse>>> getSubTasks(
            @PathVariable UUID taskId) {
        List<SubTaskResponse> response = taskService.getSubTasks(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Promote sub-task thành task độc lập (shortcut)")
    @PostMapping("/{subtaskId}/promote")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> promoteSubTask(
            @PathVariable UUID subtaskId,
            @RequestBody(required = false) PromoteSubTaskRequest request) {
        TaskDetailResponse response =
            taskService.promoteSubTask(subtaskId, request, getCurrentUserId(), null);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã chuyển thành task độc lập"));
    }

    @Operation(summary = "Lấy dependencies/links (shortcut, không cần projectId)")
    @GetMapping({"/{taskId}/dependencies", "/{taskId}/links"})
    public ResponseEntity<ApiResponse<TaskDependenciesResponse>> getDependencies(
            @PathVariable UUID taskId) {
        TaskDependenciesResponse response = taskDependencyService.getDependencies(null, taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Thêm dependency/link (shortcut)")
    @PostMapping({"/{taskId}/dependencies", "/{taskId}/links"})
    public ResponseEntity<ApiResponse<DependencyResponse>> addDependency(
            @PathVariable UUID taskId,
            @Valid @RequestBody AddDependencyRequest request) {
        DependencyResponse response = taskDependencyService.addDependency(null, taskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Xóa dependency/link (shortcut)")
    @DeleteMapping({"/{taskId}/dependencies/{linkId}", "/{taskId}/links/{linkId}"})
    public ResponseEntity<ApiResponse<Void>> removeDependency(
            @PathVariable UUID taskId,
            @PathVariable UUID linkId) {
        taskDependencyService.removeDependency(null, taskId, linkId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Chưa đăng nhập hoặc phiên làm việc hết hạn");
        }
        return userDetail.getId();
    }
}
