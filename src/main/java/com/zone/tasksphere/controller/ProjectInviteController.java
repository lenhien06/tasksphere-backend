package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.ProjectInviteResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.dto.response.VerifyInviteResponse;
import com.zone.tasksphere.entity.ProjectInvite;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.service.ProjectMemberService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Project Invites", description = "Xử lý lời mời tham gia dự án (Xác thực & Chấp nhận)")
public class ProjectInviteController {

    private final ProjectMemberService projectMemberService;

    private UserDetail requireAuth() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null) {
            throw new CustomAuthenticationException("Bạn cần đăng nhập để thực hiện thao tác này.");
        }
        return userDetail;
    }

    @Operation(summary = "[Deprecated] /invites/verify — đã bỏ", description = "Luôn 404 (kể cả ?token=). Dùng GET /api/v1/invites/{token}.")
    @GetMapping("/api/v1/invites/verify")
    public ResponseEntity<ApiResponse<VerifyInviteResponse>> legacyVerifyInviteRemoved() {
        throw new StructuredApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "Endpoint không còn tồn tại. Sử dụng GET /api/v1/invites/{token}.");
    }

    @Operation(summary = "Xác thực Token lời mời (public)", description = "SRS 4.3.2.1 — token trên path, không dùng query string.")
    @GetMapping("/api/v1/invites/{token}")
    public ResponseEntity<ApiResponse<VerifyInviteResponse>> verifyInviteByPath(@PathVariable String token) {
        ProjectInvite invite = projectMemberService.verifyInviteToken(token);
        VerifyInviteResponse response = VerifyInviteResponse.builder()
                .projectId(invite.getProject().getId())
                .projectName(invite.getProject().getName())
                .inviterName(invite.getInvitedBy().getFullName())
                .inviteeEmail(invite.getInviteeEmail())
                .role(invite.getProjectRole())
                .expiresAt(invite.getExpiresAt())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response, "Token hợp lệ."));
    }

    @Operation(summary = "Chấp nhận lời mời", description = "Người dùng đã đăng nhập click magic link từ email để tham gia dự án.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/invites/{token}/accept")
    public ResponseEntity<ApiResponse<Map<String, Object>>> acceptInvite(@PathVariable String token) {
        UUID currentUserId = requireAuth().getId();
        UUID projectId = projectMemberService.acceptInvite(token, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("projectId", projectId), "Chấp nhận lời mời thành công!"));
    }

    @Operation(summary = "Từ chối lời mời", description = "Người dùng đã đăng nhập từ chối tham gia dự án.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/invites/{token}/decline")
    public ResponseEntity<ApiResponse<Void>> declineInvite(@PathVariable String token) {
        UUID currentUserId = requireAuth().getId();
        projectMemberService.declineInvite(token, currentUserId);
        return ResponseEntity.ok(ApiResponse.voidSuccess());
    }

    @Operation(summary = "Danh sách lời mời của tôi", description = "Trả về các lời mời đang PENDING dành cho user hiện tại. Dùng cho tab thông báo.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/v1/users/me/invites")
    public ResponseEntity<ApiResponse<List<ProjectInviteResponse>>> getMyInvites() {
        String email = requireAuth().getEmail();
        List<ProjectInviteResponse> invites = projectMemberService.getMyInvites(email);
        return ResponseEntity.ok(ApiResponse.success(invites));
    }
}
