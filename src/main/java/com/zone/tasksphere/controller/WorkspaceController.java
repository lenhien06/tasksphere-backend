package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateWorkspaceRequest;
import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.request.UpdateWorkspaceRequest;
import com.zone.tasksphere.dto.request.WorkspaceInviteMemberRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.UserProfileResponse;
import com.zone.tasksphere.dto.response.WorkspaceInviteListResponse;
import com.zone.tasksphere.dto.response.WorkspaceInviteResponse;
import com.zone.tasksphere.dto.response.WorkspaceMemberResponse;
import com.zone.tasksphere.dto.response.WorkspaceResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.InviteStatus;
import com.zone.tasksphere.service.WorkspaceService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
@Tag(name = "10. Workspace Management", description = "Quản lý Workspace — tạo, mời thành viên, quản lý dự án con.")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    // ─────────────────────────────────────────────────────────────────────────────
    // Workspace CRUD
    // ─────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Tạo workspace mới")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> createWorkspace(
            @Valid @RequestBody CreateWorkspaceRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        WorkspaceResponse response = workspaceService.createWorkspace(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Workspace đã được tạo thành công"));
    }

    @Operation(summary = "Danh sách workspace của user hiện tại")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> getMyWorkspaces() {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<WorkspaceResponse> workspaces = workspaceService.getMyWorkspaces(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }

    @Operation(summary = "Chi tiết workspace theo slug")
    @GetMapping("/{slug}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getBySlug(@PathVariable String slug) {
        UserDetail currentUser = AuthUtils.getUserDetail();
        UUID userId = currentUser != null ? currentUser.getId() : null;

        WorkspaceResponse response = workspaceService.getBySlug(slug, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật workspace (OWNER hoặc ADMIN)")
    @PutMapping("/{wsId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> updateWorkspace(
            @PathVariable UUID wsId,
            @Valid @RequestBody UpdateWorkspaceRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        WorkspaceResponse response = workspaceService.updateWorkspace(wsId, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Workspace đã được cập nhật"));
    }

    @Operation(summary = "Xoá workspace (OWNER only) — soft delete, project con về standalone")
    @DeleteMapping("/{wsId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteWorkspace(@PathVariable UUID wsId) {
        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        workspaceService.deleteWorkspace(wsId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Workspace đã được xoá"));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Member management
    // ─────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Mời thành viên vào workspace (OWNER hoặc ADMIN)")
    @PostMapping("/{wsId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkspaceInviteResponse>> inviteMember(
            @PathVariable UUID wsId,
            @Valid @RequestBody WorkspaceInviteMemberRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        WorkspaceInviteResponse response = workspaceService.inviteMember(wsId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, response.isAddedToWorkspace()
                        ? "Thành viên đã được thêm vào workspace và email đã được gửi"
                        : "Email mời đã được gửi"));
    }

    @Operation(summary = "Lấy danh sách lời mời workspace")
    @GetMapping("/{wsId}/invites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInvites(
            @PathVariable UUID wsId,
            @RequestParam(defaultValue = "PENDING") InviteStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        var page = workspaceService.getInvitesByStatus(wsId, currentUser.getId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.successPage(page, "Lấy danh sách lời mời workspace thành công."));
    }

    @Operation(summary = "Thu hồi lời mời workspace")
    @DeleteMapping("/{wsId}/invites/{inviteId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> revokeInvite(
            @PathVariable UUID wsId,
            @PathVariable UUID inviteId) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        workspaceService.revokeInvite(wsId, inviteId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã thu hồi lời mời workspace."));
    }

    @Operation(summary = "Gửi lại lời mời workspace")
    @PostMapping("/{wsId}/invites/{inviteId}/resend")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> resendInvite(
            @PathVariable UUID wsId,
            @PathVariable UUID inviteId) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        workspaceService.resendInvite(wsId, inviteId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi lại lời mời workspace."));
    }

    @Operation(summary = "Danh sách thành viên + skill tags")
    @GetMapping("/{wsId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<WorkspaceMemberResponse>>> getMembers(@PathVariable UUID wsId) {
        List<WorkspaceMemberResponse> members = workspaceService.getMembers(wsId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @Operation(summary = "Xem profile của một thành viên trong workspace")
    @GetMapping("/{wsId}/members/{userId}/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMemberProfile(
            @PathVariable UUID wsId,
            @PathVariable UUID userId) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        UserProfileResponse response = workspaceService.getMemberProfile(wsId, userId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật skill của thành viên")
    @PatchMapping("/{wsId}/members/{userId}/skills")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<WorkspaceMemberResponse>> updateMemberSkills(
            @PathVariable UUID wsId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberSkillsRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        WorkspaceMemberResponse response = workspaceService.updateMemberSkills(wsId, userId, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Skill tags đã được cập nhật"));
    }

    @Operation(summary = "Xoá thành viên khỏi workspace")
    @DeleteMapping("/{wsId}/members/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID wsId,
            @PathVariable UUID userId) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        workspaceService.removeMember(wsId, userId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Thành viên đã được xoá khỏi workspace"));
    }
}
