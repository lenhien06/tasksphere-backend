package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.dto.response.VerifyWorkspaceInviteResponse;
import com.zone.tasksphere.dto.response.WorkspaceInviteListResponse;
import com.zone.tasksphere.dto.response.WorkspaceResponse;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.WorkspaceService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspace-invites")
@RequiredArgsConstructor
@Tag(name = "Workspace Invites", description = "Verify and respond to workspace invitations")
public class WorkspaceInviteController {

    private final WorkspaceService workspaceService;

    private UserDetail requireAuth() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null) {
            throw new CustomAuthenticationException("Bạn cần đăng nhập để thực hiện thao tác này.");
        }
        return userDetail;
    }

    @Operation(summary = "Lời mời workspace của tôi", description = "Trả về các lời mời workspace đang PENDING dành cho user hiện tại.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/v1/users/me/workspace-invites")
    public ResponseEntity<ApiResponse<List<WorkspaceInviteListResponse>>> getMyWorkspaceInvites() {
        String email = requireAuth().getEmail();
        List<WorkspaceInviteListResponse> invites = workspaceService.getMyWorkspaceInvites(email);
        return ResponseEntity.ok(ApiResponse.success(invites));
    }

    @Operation(summary = "Verify workspace invite token")
    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<VerifyWorkspaceInviteResponse>> verifyInvite(@PathVariable String token) {
        VerifyWorkspaceInviteResponse response = workspaceService.verifyInviteToken(token);
        return ResponseEntity.ok(ApiResponse.success(response, "Workspace invite token is valid."));
    }

    @Operation(summary = "Accept workspace invite")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{token}/accept")
    public ResponseEntity<ApiResponse<WorkspaceResponse>> acceptInvite(@PathVariable String token) {
        UserDetail currentUser = requireAuth();
        WorkspaceResponse response = workspaceService.acceptInvite(token, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Workspace invite accepted successfully."));
    }

    @Operation(summary = "Decline workspace invite")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{token}/decline")
    public ResponseEntity<ApiResponse<Void>> declineInvite(@PathVariable String token) {
        UserDetail currentUser = requireAuth();
        workspaceService.declineInvite(token, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.voidSuccess());
    }
}
