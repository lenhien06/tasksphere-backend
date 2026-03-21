package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.NotificationResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.NotificationType;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "12. Notifications", description = "Inbox thông báo. Real-time qua WebSocket.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(
        summary = "Lấy danh sách thông báo",
        description = """
            **Phân trang:** 20 items/trang, sort createdAt DESC.
            
            **QUAN TRỌNG:** Response luôn bao gồm `unreadCount`
            để FE cập nhật badge mà không cần gọi API riêng.
            
            **11 Notification Types:**
            TASK_ASSIGNED, TASK_STATUS_CHANGED, TASK_COMMENTED,
            TASK_MENTIONED, TASK_DUE_SOON, TASK_OVERDUE,
            SPRINT_STARTED, SPRINT_COMPLETED,
            PROJECT_INVITED, PROJECT_ROLE_CHANGED, MEMBER_JOINED
            """,
        parameters = {
            @Parameter(name = "isRead", description = "true/false — lọc đã đọc/chưa đọc"),
            @Parameter(name = "type",   description = "Loại notification"),
            @Parameter(name = "page",   description = "Số trang"),
            @Parameter(name = "size",   description = "Số item/trang (default 20)")
        }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) NotificationType type,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<NotificationResponse> response =
            notificationService.getNotifications(getCurrentUserId(), isRead, type, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Đánh dấu đã đọc một thông báo",
        description = """
            Set is_read = true cho 1 notification.
            Invalidate Redis cache unread-count cho user.
            
            **Quyền:** Chỉ recipient của notification.
            """
    )
    @PatchMapping("/{notifId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID notifId) {
        notificationService.markAsRead(notifId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }

    @Operation(
        summary = "Đánh dấu đã đọc TẤT CẢ",
        description = """
            **Atomic operation:** UPDATE WHERE recipient = currentUser AND is_read = false.
            
            Trả về markedCount = số notification được đánh dấu đọc.
            Invalidate Redis cache unread-count.
            """
    )
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead() {
        int count = notificationService.markAllAsRead(getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(count, "Đã đánh dấu đọc " + count + " thông báo"));
    }

    @Operation(
        summary = "Lấy số notification chưa đọc (Badge count)",
        description = """
            **Redis cache:** Count được cache trong Redis, invalidate khi read.
            **Dùng cho:** Badge số đỏ trên chuông notification trong header.
            """
    )
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        long count = notificationService.getUnreadCount(getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @Operation(
        summary = "Xóa một thông báo",
        description = "Soft-delete thông báo. Chỉ recipient mới được xóa."
    )
    // FIX: P5-BE-05 - Thêm DELETE endpoint theo SRS
    @DeleteMapping("/{notifId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable UUID notifId) {
        notificationService.deleteNotification(notifId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }
}
