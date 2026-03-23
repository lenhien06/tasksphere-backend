package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateCommentRequest;
import com.zone.tasksphere.dto.request.UpdateCommentRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.CommentResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.CommentService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "6. Comments", description = "Bình luận trên task. Hỗ trợ @mention và Rich Text HTML.")
@SecurityRequirement(name = "bearerAuth")
public class CommentController {

    private final CommentService commentService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(
        summary = "Thêm bình luận",
        description = """
            **FR-21 + FR-22:** Tạo comment HTML với hỗ trợ @mention.

            **HTML Sanitize (Jsoup):** Content được làm sạch server-side
            để chống XSS trước khi lưu.

            **@Mention:** TipTap editor tạo HTML:
            `<span data-mention-id="{userId}" data-mention-name="{name}">@Name</span>`
            Backend parse data-mention-id → gửi TASK_MENTIONED notification
            đến user được tag (trong vòng 5 giây nếu online).

            **Chỉ mention member của project** — user ngoài bị bỏ qua.
            """
    )
    @PostMapping("/projects/{projectId}/tasks/{taskId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.addComment(projectId, taskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách bình luận của task")
    @GetMapping("/projects/{projectId}/tasks/{taskId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getComments(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<CommentResponse> response = commentService.getComments(projectId, taskId, pageable, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Sửa bình luận",
        description = """
            **Quyền:** Chỉ author của comment (không phải PM hay admin).

            **isEdited:** Tự động set = true sau khi sửa.
            Content mới cũng được sanitize Jsoup.
            **@mention:** Mention mới (so với trước khi sửa) → gửi TASK_MENTIONED như khi tạo comment.
            """
    )
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {
        CommentResponse response = commentService.updateComment(commentId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Xóa bình luận",
        description = """
            **Quyền:** Author của comment HOẶC PM.

            **BR-24:** Soft delete — deleted_at = NOW().
            Comment không hiển thị nhưng vẫn còn trong DB.
            """
    )
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable UUID commentId) {
        commentService.deleteComment(commentId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }
}
