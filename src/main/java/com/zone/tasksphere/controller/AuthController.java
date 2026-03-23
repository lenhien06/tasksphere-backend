package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.AuthResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.security.CustomUserDetailsService;
import com.zone.tasksphere.service.AuthService;
import com.zone.tasksphere.utils.CookieUtils;
import com.zone.tasksphere.utils.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "1. Authentication", description = "Đăng nhập, đăng ký, refresh token, quên mật khẩu")
public class AuthController {

    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final CookieUtils cookieUtils;
    private final com.zone.tasksphere.repository.UserRepository userRepository;

    @Operation(
        summary = "Xem hồ sơ cá nhân của mình",
        description = "Trả về thông tin đầy đủ của user đang đăng nhập.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDetail>> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new CustomAuthenticationException("Chưa đăng nhập hoặc phiên làm việc hết hạn");
        }

        String contact = auth.getName();
        com.zone.tasksphere.entity.User user = userRepository.findByEmail(contact)
                .orElseThrow(() -> new CustomAuthenticationException("Không tìm thấy người dùng"));

        UserDetail userDetail = UserDetail.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .systemRole(user.getSystemRole())
                .role(user.getRole() != null ? new com.zone.tasksphere.dto.response.RoleDto(user.getRole()) : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(userDetail));
    }

    @Operation(
        summary = "Đăng nhập",
        description = """
            Xác thực người dùng bằng email/password.
            
            **Business Rules:**
            - BR-02: Khóa tài khoản 15 phút sau 5 lần sai liên tiếp
            - BR-03: Access token TTL 1 giờ, Refresh token TTL 7 ngày
            
            **Khi thành công:** Reset loginAttempts về 0, cập nhật lastLoginAt.
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng nhập thành công",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Email/password không đúng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "423", description = "Tài khoản bị khóa (BR-02) — thử lại sau {lockUntil}")
        }
    )
    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request, httpServletRequest);
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Đăng nhập thành công"));
    }

    @Operation(
        summary = "Đăng ký tài khoản mới",
        description = """
            Tạo tài khoản người dùng mới trong hệ thống.
            
            **Validate:**
            - BR-06: Email phải unique trong toàn hệ thống
            - BR-07: Password ≥ 8 ký tự, có chữ hoa/thường/số/ký tự đặc biệt
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo tài khoản thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email đã tồn tại (BR-06)")
        }
    )
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.signup(request, httpServletRequest);
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Đăng ký tài khoản thành công"));
    }

    @Operation(
        summary = "Gửi mã OTP đăng ký",
        description = "Gửi mã OTP 6 số về Email để đăng ký tài khoản"
    )
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@RequestParam String email) {
        authService.sendRegistrationOtp(email);
        return ResponseEntity.ok(ApiResponse.success(null, "Mã OTP đã được gửi tới email " + email));
    }

    @Operation(
        summary = "Quên mật khẩu",
        description = """
            Gửi email chứa link đặt lại mật khẩu.
            
            **FR-04:** Reset token = UUID ngẫu nhiên, TTL 30 phút lưu trong Redis.
            **Bảo mật:** Luôn trả 200 dù email tồn tại hay không (chống email enumeration).
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email đã được gửi (nếu tồn tại)")
        }
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Mã xác nhận đã được gửi tới email của bạn"));
    }

    @Operation(
        summary = "Đặt lại mật khẩu",
        description = """
            Đặt mật khẩu mới bằng reset token nhận từ email.
            
            **FR-04:** Token hết hạn sau 30 phút. Sau khi đổi thành công,
            token bị xóa khỏi Redis — không dùng lại được.
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đổi mật khẩu thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Token không hợp lệ hoặc đã hết hạn")
        }
    )
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Mật khẩu của bạn đã được cập nhật thành công"));
    }

    @Operation(
        summary = "Đăng xuất",
        description = """
            Vô hiệu hóa cả access token và refresh token hiện tại.
            
            **BR-03:** Sau logout, cả 2 token được đưa vào blacklist trong Redis.
            Mọi request tiếp theo với token cũ sẽ nhận 401.
            """,
        security = @SecurityRequirement(name = "bearerAuth"),
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đăng xuất thành công")
        }
    )
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @org.springframework.web.bind.annotation.RequestBody(required = false) LogoutRequest body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String accessToken = null;
        String refreshToken = null;

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (CookieUtils.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    if (accessToken == null) accessToken = cookie.getValue();
                } else if (CookieUtils.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                }
            }
        }

        // BFF calls logout server-to-server (no browser cookies forwarded),
        // so the refresh token is passed in the request body instead.
        if (refreshToken == null && body != null && body.getRefreshToken() != null) {
            refreshToken = body.getRefreshToken();
        }

        if (accessToken != null) {
            try { jwtUtils.revokeToken(accessToken); } catch (Exception ignored) { }
        }
        if (refreshToken != null) {
            try { jwtUtils.revokeToken(refreshToken); } catch (Exception ignored) { }
        }

        cookieUtils.deleteAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success(null, "Đăng xuất thành công"));
    }

    @Operation(
        summary = "Làm mới Access Token",
        description = """
            Dùng Refresh Token (còn hạn) để lấy Access Token mới.
            
            **BR-03:** Refresh token TTL 7 ngày. Nếu refresh token đã expire → 401,
            user phải đăng nhập lại.
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả về accessToken mới"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Refresh token không hợp lệ hoặc đã expire")
        }
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @RequestBody(required = false) TokenRefreshRequest request,
            HttpServletRequest httpServletRequest,
            HttpServletResponse response
    ) {
        String refreshToken = null;

        // 1. Thử lấy từ Body
        if (request != null && request.getRefreshToken() != null) {
            refreshToken = request.getRefreshToken();
        }

        // 2. Nếu Body không có, thử lấy từ Cookie
        if (refreshToken == null && httpServletRequest.getCookies() != null) {
            for (var cookie : httpServletRequest.getCookies()) {
                if (CookieUtils.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            throw new CustomAuthenticationException("Không tìm thấy Refresh Token trong request");
        }

        AuthResponse authResponse = authService.refreshToken(refreshToken);

        // Cập nhật lại Cookies cho trình duyệt
        cookieUtils.setAuthCookies(response, authResponse.getAccessToken(), authResponse.getRefreshToken());

        return ResponseEntity.ok(ApiResponse.success(authResponse, "Làm mới token thành công"));
    }
}
