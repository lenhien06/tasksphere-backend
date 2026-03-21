package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.AssignVersionRequest;
import com.zone.tasksphere.dto.request.CreateVersionRequest;
import com.zone.tasksphere.dto.request.UpdateVersionRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.DeleteVersionResponse;
import com.zone.tasksphere.dto.response.TaskResponse;
import com.zone.tasksphere.dto.response.VersionResponse;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.VersionService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "19. Versions", description = "Quản lý Version phát hành (P4-BE-06)")
@SecurityRequirement(name = "bearerAuth")
public class VersionController {

    private final VersionService versionService;

    @Operation(summary = "Tạo Version mới", description = "PM only. name unique trong project.")
    @PostMapping("/api/v1/projects/{projectId}/versions")
    public ResponseEntity<ApiResponse<VersionResponse>> createVersion(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateVersionRequest request) {
        VersionResponse response = versionService.createVersion(projectId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Version đã được tạo thành công"));
    }

    @Operation(summary = "Lấy danh sách Version của dự án",
               description = "All members. Kèm taskCount và doneCount.")
    @GetMapping("/api/v1/projects/{projectId}/versions")
    public ResponseEntity<ApiResponse<List<VersionResponse>>> getVersionsByProject(
            @PathVariable UUID projectId) {
        List<VersionResponse> response = versionService.getVersionsByProject(projectId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật Version",
               description = "PM only. RELEASED không thể hạ về PLANNING/IN_PROGRESS.")
    @PutMapping("/api/v1/versions/{versionId}")
    public ResponseEntity<ApiResponse<VersionResponse>> updateVersion(
            @PathVariable UUID versionId,
            @Valid @RequestBody UpdateVersionRequest request) {
        VersionResponse response = versionService.updateVersion(versionId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Version đã được cập nhật"));
    }

    @Operation(summary = "Xóa Version",
               description = "PM only. Không xóa được version RELEASED. Tasks sẽ bị unlink.")
    @DeleteMapping("/api/v1/versions/{versionId}")
    public ResponseEntity<ApiResponse<DeleteVersionResponse>> deleteVersion(
            @PathVariable UUID versionId) {
        DeleteVersionResponse response = versionService.deleteVersion(versionId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Gán Task vào Version",
               description = "PM only. versionId = null để bỏ gán. TSK_007: không gán vào RELEASED version.")
    @PatchMapping("/api/v1/tasks/{taskId}/version")
    public ResponseEntity<ApiResponse<TaskResponse>> assignTaskVersion(
            @PathVariable UUID taskId,
            @RequestBody AssignVersionRequest request) {
        TaskResponse response = versionService.assignTaskVersion(taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Version đã được cập nhật cho task"));
    }

    // ── Helper ───────────────────────────────────────────────────────

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new CustomAuthenticationException("Chưa đăng nhập hoặc phiên làm việc hết hạn");
        }
        CustomUserDetail userDetail = (CustomUserDetail) auth.getPrincipal();
        return userDetail.getUserDetail().getId();
    }
}
