package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.DirectMemberRequest;
import com.zone.tasksphere.dto.request.InviteMemberRequest;
import com.zone.tasksphere.dto.request.UpdateRoleRequest;
import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.entity.enums.InviteStatus;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.ProjectMemberService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "3. Project Management", description = "Quản lý dự án — tạo, sửa, archive, quản lý thành viên.")
@SecurityRequirement(name = "bearerAuth")
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(
        summary = "Danh sách thành viên dự án",
        description = """
            Lấy danh sách thành viên kèm role và thời gian tham gia.
            
            **Quyền:** Mọi thành viên của dự án.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/{projectId}/members")
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> listMembers(@PathVariable UUID projectId) {
        List<ProjectMemberResponse> members = projectMemberService.getProjectMembers(projectId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @Operation(
        summary = "Mời thành viên vào dự án",
        description = """
            **FR-12:** Thêm user vào dự án với role cho trước.
            
            **Validate:**
            - User chưa là thành viên → 409 nếu đã là member
            - BR-12: Check giới hạn member theo plan (Free=5, Pro=50)
            - User phải tồn tại trong hệ thống
            
            **Sau khi thêm:** Gửi email thông báo cho member mới.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PostMapping("/{projectId}/members")
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> addMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody DirectMemberRequest request
    ) {
        UUID currentUserId = getCurrentUserId();
        ProjectMemberResponse response = projectMemberService.addMemberDirectly(projectId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Thêm thành viên thành công."));
    }

    @Operation(
        summary = "Xóa thành viên khỏi dự án",
        description = """
            **FR-12:** Xóa member và reassign task của họ.
            
            **BR-09:** Owner KHÔNG thể bị xóa → 403 CANNOT_REMOVE_OWNER.
            
            **Sau khi xóa:**
            - Task của member đó → assignee = NULL (unassigned)
            - Ghi activity log sự kiện MEMBER_REMOVED
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId
    ) {
        UUID currentUserId = getCurrentUserId();
        projectMemberService.removeMember(projectId, userId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa thành viên khỏi dự án."));
    }

    @Operation(
        summary = "Tìm kiếm thành viên để @mention",
        description = """
            Autocomplete danh sách thành viên khi gõ @ trong comment.
            
            **Performance:** ≤ 200ms, trả về tối đa 10 kết quả.
            **Chỉ trả về:** member của project này, không phải toàn bộ user.
            """,
        parameters = {
            @Parameter(name = "q", description = "Từ khóa tìm (tên hoặc email)", required = true,
                       example = "nguyen")
        }
    )
    @GetMapping("/{projectId}/members/search")
    public ResponseEntity<ApiResponse<List<MemberSearchResponse>>> searchMembers(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "") String q) {
        UUID currentUserId = getCurrentUserId();
        List<MemberSearchResponse> results =
            projectMemberService.searchMembers(projectId, q, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @Operation(summary = "Mời thành viên mới (SaaS B2B)", description = "Gửi lời mời tham gia dự án qua email.")
    @PostMapping("/{projectId}/invites")
    public ResponseEntity<ApiResponse<InviteMemberResponse>> inviteMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody InviteMemberRequest request
    ) {
        UUID currentUserId = getCurrentUserId();
        InviteMemberResponse response = projectMemberService.inviteMember(projectId, request, currentUserId);

        String message = response.isNewUser()
                ? "Người dùng chưa có tài khoản. Lời mời qua email đã được gửi."
                : "Đã gửi lời mời thành viên qua email.";

        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @Operation(summary = "Cập nhật Role", description = "Thay đổi vai trò của một thành viên trong dự án.")
    @PatchMapping("/{projectId}/members/{userId}/role")
    public ResponseEntity<ApiResponse<Void>> updateMemberRole(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        UUID currentUserId = getCurrentUserId();
        projectMemberService.changeMemberRole(projectId, userId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật vai trò thành viên thành công."));
    }

    @Operation(summary = "Lấy danh sách lời mời", description = "Lấy danh sách lời mời của dự án.")
    @GetMapping("/{projectId}/invites")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInvites(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "PENDING") InviteStatus status,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID currentUserId = getCurrentUserId();
        var page = projectMemberService.getInvitesByStatus(projectId, currentUserId, status, pageable);
        return ResponseEntity.ok(
                ApiResponse.successPage(page, "Lấy danh sách lời mời thành công.")
        );
    }

    @Operation(summary = "Thu hồi lời mời", description = "Hủy bỏ một lời mời đang chờ duyệt.")
    @DeleteMapping("/{projectId}/invites/{inviteId}")
    public ResponseEntity<ApiResponse<Void>> revokeInvite(
            @PathVariable UUID projectId,
            @PathVariable UUID inviteId
    ) {
        UUID currentUserId = getCurrentUserId();
        projectMemberService.revokeInvite(projectId, inviteId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã thu hồi lời mời thành công."));
    }

    @Operation(summary = "Gửi lại email lời mời", description = "Gửi lại email nhắc nhở cho người được mời.")
    @PostMapping("/{projectId}/invites/{inviteId}/resend")
    public ResponseEntity<ApiResponse<Void>> resendInvite(
            @PathVariable UUID projectId,
            @PathVariable UUID inviteId
    ) {
        UUID currentUserId = getCurrentUserId();
        projectMemberService.resendInvite(projectId, inviteId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã gửi lại lời mời"));
    }

    @Operation(summary = "Tự rời dự án", description = "Thành viên tự nguyện rời khỏi dự án.")
    @DeleteMapping("/{projectId}/members/leave")
    public ResponseEntity<ApiResponse<Void>> leaveProject(
            @PathVariable UUID projectId
    ) {
        UUID currentUserId = getCurrentUserId();
        projectMemberService.leaveMember(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã rời dự án thành công."));
    }
}
