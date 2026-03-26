package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.AddDependencyRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.DependencyResponse;
import com.zone.tasksphere.dto.response.TaskDependenciesResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.TaskDependencyService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "11. Task Dependencies / Links", description = "Quản lý liên kết giữa các task: BLOCKS, BLOCKED_BY, RELATES_TO, DUPLICATES.")
@SecurityRequirement(name = "bearerAuth")
public class TaskDependencyController {

    private final TaskDependencyService dependencyService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    // ── /links endpoints (spec-aligned) ──────────────────────────────

    @Operation(summary = "Thêm link giữa hai task",
        description = """
            **FR-33 + BR-28:** Tạo liên kết giữa task hiện tại và targetTask.

            **linkType options:**
            - `BLOCKS`: task này block targetTask (targetTask phải chờ task này DONE)
            - `BLOCKED_BY`: task này bị block bởi targetTask
            - `RELATES_TO`: liên quan, không block
            - `DUPLICATES`: task này là bản sao của targetTask
            - `IS_DUPLICATED_BY`: targetTask là bản sao của task này

            **Auto-inverse:** Inverse record tự động được tạo cho targetTask.
            **BR-28:** Circular dependency BLOCKS bị phát hiện và từ chối (HTTP 422).
            """)
    @PostMapping("/api/v1/projects/{projectId}/tasks/{taskId}/links")
    public ResponseEntity<ApiResponse<DependencyResponse>> addLink(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody AddDependencyRequest request) {
        DependencyResponse response = dependencyService.addDependency(projectId, taskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách links của task")
    @GetMapping("/api/v1/projects/{projectId}/tasks/{taskId}/links")
    public ResponseEntity<ApiResponse<TaskDependenciesResponse>> getLinks(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId) {
        TaskDependenciesResponse response = dependencyService.getDependencies(projectId, taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Xóa link",
        description = "Xóa liên kết và inverse record tương ứng. Auth: MEMBER hoặc PM.")
    @DeleteMapping("/api/v1/projects/{projectId}/tasks/{taskId}/links/{linkId}")
    public ResponseEntity<ApiResponse<Void>> removeLink(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID linkId) {
        dependencyService.removeDependency(projectId, taskId, linkId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }

    // ── /dependencies endpoints (backward compatibility) ───────────────

    @Operation(summary = "Thêm mối quan hệ phụ thuộc (deprecated, dùng /links)")
    @PostMapping("/api/v1/projects/{projectId}/tasks/{taskId}/dependencies")
    public ResponseEntity<ApiResponse<DependencyResponse>> addDependency(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody AddDependencyRequest request) {
        DependencyResponse response = dependencyService.addDependency(projectId, taskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách task phụ thuộc (deprecated, dùng /links)")
    @GetMapping("/api/v1/projects/{projectId}/tasks/{taskId}/dependencies")
    public ResponseEntity<ApiResponse<TaskDependenciesResponse>> getDependencies(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId) {
        TaskDependenciesResponse response = dependencyService.getDependencies(projectId, taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Xóa mối quan hệ phụ thuộc (deprecated, dùng /links/{linkId})")
    @DeleteMapping("/api/v1/projects/{projectId}/tasks/{taskId}/dependencies/{depId}")
    public ResponseEntity<ApiResponse<Void>> removeDependency(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID depId) {
        dependencyService.removeDependency(projectId, taskId, depId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }
}
