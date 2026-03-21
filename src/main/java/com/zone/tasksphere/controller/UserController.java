package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateUserRequest;
import com.zone.tasksphere.dto.request.NotifPrefsRequest;
import com.zone.tasksphere.dto.request.UpdateProfileRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.service.UserService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "2. User Management", description = "Admin quản lý tài khoản — tạo, khóa, phân quyền. User tự cập nhật hồ sơ.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(
        summary = "[Admin] Danh sách tất cả user",
        description = """
            Lấy danh sách người dùng với tìm kiếm và lọc.
            
            **Quyền:** ADMIN
            **FR-06:** Tìm kiếm theo fullName, email. Lọc theo status, role.
            """,
        parameters = {
            @Parameter(name = "q",      description = "Tìm theo tên hoặc email"),
            @Parameter(name = "status", description = "ACTIVE | INACTIVE | SUSPENDED"),
            @Parameter(name = "page",   description = "Số trang (bắt đầu từ 0)", example = "0"),
            @Parameter(name = "size",   description = "Số item mỗi trang",       example = "20")
        }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserDetail>>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long roleId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<UserDetail> response = userService.listUsers(q, status, roleId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "[Admin] Tạo tài khoản user mới",
        description = """
            Admin tạo tài khoản cho nhân viên.
            
            **FR-05:** Gửi email thông báo tài khoản vừa tạo cho user.
            **BR-07:** Mật khẩu tuân thủ chính sách bảo mật.
            """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<UserDetail>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDetail response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(
        summary = "Xem hồ sơ cá nhân của mình",
        description = "Trả về thông tin đầy đủ của user đang đăng nhập.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDetail>> getMyProfile() {
        UserDetail currentUser = AuthUtils.getUserDetail();
        return ResponseEntity.ok(ApiResponse.success(currentUser));
    }

    @Operation(
        summary = "Cập nhật hồ sơ cá nhân",
        description = """
            User tự cập nhật thông tin của mình.
            
            **FR-09:** Có thể cập nhật: fullName, avatarUrl, timezone, weekdaysOnly,
            emailDailyDigest.
            **Không thể:** thay đổi email, role, trạng thái tài khoản.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserDetail>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = AuthUtils.getUserDetail().getId();
        UserDetail response = userService.updateMyProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "[Admin] Khóa tài khoản",
        description = """
            **FR-08:** Admin khóa tài khoản → user không thể đăng nhập.
            **BR-08:** Dữ liệu (task, comment, log) của user vẫn được giữ nguyên.
            Chỉ đổi status = SUSPENDED, KHÔNG xóa dữ liệu.
            """
    )
    @PatchMapping("/{userId}/lock")
    public ResponseEntity<ApiResponse<Void>> lockUser(@PathVariable UUID userId) {
        userService.lockUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Tài khoản đã bị khóa"));
    }

    @Operation(
        summary = "[Admin] Mở khóa tài khoản",
        description = "Đổi status từ SUSPENDED/INACTIVE → ACTIVE."
    )
    @PatchMapping("/{userId}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable UUID userId) {
        userService.unlockUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Tài khoản đã được mở khóa"));
    }

    @Operation(
        summary = "Cài đặt thông báo",
        description = """
            Cập nhật preferences nhận thông báo của user.
            
            **FR-47:** emailDailyDigest — bật/tắt email tổng hợp sáng 7:00 MON-FRI.
            **weekdaysOnly** — chỉ nhận email ngày làm việc.
            **timezone** — múi giờ hiển thị (IANA, VD: Asia/Ho_Chi_Minh).
            **typePreferences** — bật/tắt từng loại notification.
            """,
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @PutMapping("/me/notification-preferences")
    public ResponseEntity<ApiResponse<Void>> updateNotificationPreferences(@Valid @RequestBody NotifPrefsRequest request) {
        UUID userId = AuthUtils.getUserDetail().getId();
        userService.updateNotificationPreferences(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật cài đặt thông báo thành công"));
    }
}
