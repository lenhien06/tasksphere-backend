package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.ProjectDeleteRequest;
import com.zone.tasksphere.dto.request.ProjectRequest;
import com.zone.tasksphere.dto.request.ProjectUpdateRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.ColumnResponse;
import com.zone.tasksphere.dto.response.ProjectResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.ProjectService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "3. Project Management", description = "Quản lý dự án — tạo, sửa, archive, quản lý thành viên.")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    @Operation(
        summary = "Danh sách dự án",
        description = """
            **FR-11:** Lấy danh sách dự án trong workspace cá nhân của user.
            ADMIN thấy tất cả dự án.

            **BR-31 — Visibility trong danh sách:**
            - private/internal: chỉ owner/member thấy
            - public: không hiện đại trà; chỉ hiện với owner/member
              hoặc user đã từng mở dự án qua shared link trước đó

            **Link access:**
            - public project vẫn có thể mở trực tiếp bằng shared link
            - sau khi user đã mở link, dự án sẽ được lưu vào danh sách của họ

            **Filter:** status (active/completed/archived), search theo tên.
            """,
        parameters = {
            @Parameter(name = "q",      description = "Tìm theo tên dự án"),
            @Parameter(name = "status", description = "active | completed | archived")
        }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listProjects(
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(required = false, defaultValue = "active") String status,
            @RequestParam(required = false) ProjectVisibility visibility,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        UUID userId = currentUser != null ? currentUser.getId() : null;
        boolean isAdmin = currentUser != null && SystemRole.ADMIN.equals(currentUser.getSystemRole());
        
        ProjectStatus projectStatus = ProjectStatus.fromString(status);

        Page<ProjectResponse> projects = projectService.getProjects(q, projectStatus, visibility, userId, isAdmin, pageable);

        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @Operation(
        summary = "Tạo dự án mới",
        description = """
            Tạo dự án và tự động:
            1. Gán người tạo làm Owner (PM role)
            2. Tạo 3 cột Kanban mặc định: To Do / In Progress / Done
            3. Tạo activity log sự kiện PROJECT_CREATED
            
            **BR-09:** Người tạo là Owner mặc định.
            **BR-10:** Project Key unique, không đổi được sau khi tạo.
            """,
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = """
                **name:** Tên dự án (required, max 200 ký tự)
                **projectKey:** Mã viết tắt (required, 2-10 ký tự IN HOA, VD: PROJ, HR)
                **description:** Mô tả dự án (optional)
                **visibility:** private | internal | public (default: private)
                **startDate/endDate:** Ngày bắt đầu/kết thúc (optional)
                """
        )
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody ProjectRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID ownerId = currentUser.getId();
        ProjectResponse project = projectService.createProject(request, ownerId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(project, "Dự án đã được tạo thành công"));
    }

    /**
     * Literal path so {@code /ping} is not bound as {@code /{id}} (UUID) — avoids 500 on invalid UUID.
     * Endpoint không dùng; trả 404 theo contract test/legacy client.
     */
    @GetMapping("/ping")
    public ResponseEntity<Void> projectPingNotFound() {
        return ResponseEntity.notFound().build();
    }

    @Operation(summary = "Lấy chi tiết dự án theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectById(
            @Parameter(description = "UUID của dự án") @PathVariable UUID id) {
        
        UserDetail currentUser = AuthUtils.getUserDetail();
        UUID userId = currentUser != null ? currentUser.getId() : null;
        boolean isAdmin = currentUser != null && SystemRole.ADMIN.equals(currentUser.getSystemRole());
        
        ProjectResponse project = projectService.getProjectById(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @Operation(summary = "Lấy chi tiết dự án theo Project Key")
    @GetMapping("/key/{key}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProjectByKey(
            @Parameter(description = "Key duy nhất của dự án (ví dụ: DEMO)") @PathVariable String key) {
        
        UserDetail currentUser = AuthUtils.getUserDetail();
        UUID userId = currentUser != null ? currentUser.getId() : null;
        boolean isAdmin = currentUser != null && SystemRole.ADMIN.equals(currentUser.getSystemRole());
        
        ProjectResponse project = projectService.getProjectByKey(key, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @Operation(summary = "Cập nhật thông tin dự án")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @Parameter(description = "UUID của dự án") @PathVariable UUID id,
            @Valid @RequestBody ProjectUpdateRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID actorId = currentUser.getId();
        boolean isAdmin = SystemRole.ADMIN.equals(currentUser.getSystemRole());

        ProjectResponse project = projectService.updateProject(id, request, actorId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(project, "Dự án đã được cập nhật thành công"));
    }

    @Operation(
        summary = "Archive dự án",
        description = """
            **BR-11:** Archive = soft delete, dữ liệu vẫn còn.
            
            **Quy trình:**
            1. PM/Admin xác nhận (FE gọi endpoint này với confirm=true)
            2. Set deleted_at = NOW()
            3. Gửi notification đến TẤT CẢ thành viên
            4. Ghi activity log
            
            **Lưu ý:** Dự án đã archive có thể khôi phục bởi Owner hoặc Admin.
            """
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> archiveProject(
            @Parameter(description = "UUID của dự án cần xóa") @PathVariable UUID id,
            @RequestBody(required = false) ProjectDeleteRequest request,
            @RequestParam(required = false) String confirmName) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID actorId = currentUser.getId();
        boolean isAdmin = SystemRole.ADMIN.equals(currentUser.getSystemRole());

        projectService.archiveProject(id, resolveConfirmName(request, confirmName), actorId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(null, "Dự án đã được lưu trữ thành công"));
    }

    @Operation(
        summary = "Xóa vĩnh viễn dự án",
        description = """
            Xóa sạch dự án và toàn bộ dữ liệu liên quan:
            task, sprint, comment, attachment, member, filter, webhook, log, notification...

            **Lưu ý:** Đây là hard delete và không thể khôi phục.
            """
    )
    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteProject(
            @Parameter(description = "UUID của dự án cần xóa vĩnh viễn") @PathVariable UUID id,
            @RequestBody(required = false) ProjectDeleteRequest request,
            @RequestParam(required = false) String confirmName) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID actorId = currentUser.getId();
        boolean isAdmin = SystemRole.ADMIN.equals(currentUser.getSystemRole());

        projectService.deleteProjectPermanently(id, resolveConfirmName(request, confirmName), actorId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(null, "Dự án đã được xóa vĩnh viễn"));
    }

    private String resolveConfirmName(ProjectDeleteRequest request, String confirmNameParam) {
        if (request != null && request.getConfirmName() != null && !request.getConfirmName().isBlank()) {
            return request.getConfirmName();
        }
        return confirmNameParam;
    }

    @Operation(
        summary = "Khôi phục dự án đã archive",
        description = """
            **BR-11:** Dự án archived phải có thể khôi phục.
            
            **Quyền hạn:**
            - Chỉ Owner hoặc System Admin được phép khôi phục dự án đã archive.
            """
    )
    @PatchMapping("/{projectId}/restore")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ProjectResponse>> restoreProject(
            @Parameter(description = "UUID của dự án cần khôi phục") @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectService.restoreProject(projectId)));
    }

    @Operation(
        summary = "Lấy danh sách cột Kanban của dự án",
        description = "Trả về tất cả cột Kanban theo thứ tự sortOrder."
    )
    @GetMapping("/{id}/columns")
    public ResponseEntity<ApiResponse<List<ColumnResponse>>> getProjectColumns(
            @Parameter(description = "UUID của dự án") @PathVariable UUID id) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        UUID userId = currentUser != null ? currentUser.getId() : null;
        boolean isAdmin = currentUser != null && SystemRole.ADMIN.equals(currentUser.getSystemRole());

        List<ColumnResponse> columns = projectService.getProjectColumns(id, userId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(columns));
    }
}
