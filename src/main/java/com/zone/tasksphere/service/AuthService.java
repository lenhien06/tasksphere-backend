package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.AuthResponse;
import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.event.ActivityLogEvent;
import com.zone.tasksphere.exception.*;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.security.*;
import com.zone.tasksphere.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;
    private final ProjectMemberService projectMemberService;
    private final ApplicationEventPublisher eventPublisher;

    private static final String ROLE_USER = "USER";
    private static final String OTP_PREFIX = "auth:otp:";
    private static final long OTP_EXPIRY_MINUTES = 5;
    // FIX: BR-02 - Brute force protection
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    // FIX: Bug3 - Multi-tab race condition: track recently-used refresh tokens
    private static final String REUSED_REFRESH_PREFIX = "reused_refresh:";
    private static final long REUSED_REFRESH_TTL_SECONDS = 120; // 30 → 120s: đủ cho multi-tab race condition

    // =========================================================
    // 1. THÊM LẠI HÀM GỬI MÃ OTP
    // =========================================================
    @Transactional
    public void sendRegistrationOtp(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email này đã được đăng ký hệ thống");
        }
        // Random 6 số
        String otp = String.valueOf((int) ((Math.random() * (999999 - 100000)) + 100000));

        // Lưu vào Redis (Sống được 5 phút)
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

        log.info("Đang gửi mã OTP cho {}: {}", email, otp);
        try {
            emailService.sendOtpEmail(email, otp);
        } catch (EmailSendException e) {
            log.error("[OTP] Gửi OTP thất bại tới {}: {}", email, e.getMessage());
            throw new BusinessRuleException("EMAIL_SEND_FAILED: Không gửi được mã OTP, vui lòng thử lại sau.");
        }
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản với email này"));

        String otp = String.valueOf((int) ((Math.random() * (999999 - 100000)) + 100000));
        redisTemplate.opsForValue().set(OTP_PREFIX + "forgot:" + email, otp, OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

        log.info("Đang gửi mã OTP khôi phục mật khẩu cho {}: {}", email, otp);
        try {
            emailService.sendPasswordResetEmail(email, otp);
        } catch (EmailSendException e) {
            log.error("[OTP] Gửi OTP khôi phục thất bại tới {}: {}", email, e.getMessage());
            throw new BusinessRuleException("EMAIL_SEND_FAILED: Không gửi được mã OTP, vui lòng thử lại sau.");
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim();
        String savedOtp = (String) redisTemplate.opsForValue().get(OTP_PREFIX + "forgot:" + email);

        if (savedOtp == null || !savedOtp.equals(request.getOtp())) {
            throw new BadRequestException("Mã xác nhận không chính xác hoặc đã hết hạn");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTemplate.delete(OTP_PREFIX + "forgot:" + email);
    }

    // =========================================================
    // 2. ĐĂNG KÝ (Sửa lại: Dùng OTP thay cho Captcha)
    // =========================================================
    @Transactional
    public AuthResponse signup(SignupRequest request, HttpServletRequest httpServletRequest) {

        String email = request.getEmail().trim();
        String savedOtp = (String) redisTemplate.opsForValue().get(OTP_PREFIX + email);
        if (savedOtp == null || !savedOtp.equals(request.getOtp())) {
            throw new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn. Vui lòng thử lại.");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email này đã được sử dụng");
        }

        Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                .orElseThrow(() -> new BadRequestException("Cấu hình hệ thống lỗi: Role USER chưa được tạo"));

        User newUser = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .systemRole(SystemRole.USER)
                .role(defaultRole)
                .avatarUrl(generateSmartAvatar(request.getFullName()))
                .emailVerifiedAt(Instant.now())
                .build();

        userRepository.save(newUser);

        // Tự động gia nhập dự án nếu có inviteToken
        if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
            projectMemberService.acceptInviteAfterSignup(request.getInviteToken(), newUser);
        }

        redisTemplate.delete(OTP_PREFIX + email);

        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(email);
        return buildAuthResponse(userDetails, newUser);
    }

    // =========================================================
    // ĐĂNG NHẬP (LOCAL: email + password + captcha)
    // =========================================================
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {

        String contact = request.getEmail().trim();

        User user = userRepository.findByEmail(contact)
                .orElseThrow(() -> new CustomAuthenticationException("Email hoặc mật khẩu không chính xác"));

        // FIX: BR-02 - Kiểm tra tài khoản bị khóa tạm thời
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(Instant.now())) {
            throw new CustomAuthenticationException(
                "Tài khoản bị tạm khóa do đăng nhập sai nhiều lần. Thử lại sau " + LOCK_DURATION_MINUTES + " phút.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // FIX: BR-02 - Tăng loginAttempts và khóa tài khoản nếu vượt ngưỡng
            int attempts = user.getLoginAttempts() + 1;
            user.setLoginAttempts(attempts);
            if (attempts >= MAX_LOGIN_ATTEMPTS) {
                user.setLockUntil(Instant.now().plus(LOCK_DURATION_MINUTES, java.time.temporal.ChronoUnit.MINUTES));
                user.setLoginAttempts(0);
                userRepository.save(user);
                throw new CustomAuthenticationException(
                    "Đăng nhập sai " + MAX_LOGIN_ATTEMPTS + " lần liên tiếp. Tài khoản bị khóa " + LOCK_DURATION_MINUTES + " phút.");
            }
            userRepository.save(user);
            throw new CustomAuthenticationException("Email hoặc mật khẩu không chính xác");
        }

        // FIX: BR-02 - Reset loginAttempts sau khi đăng nhập thành công
        if (user.getLoginAttempts() > 0 || user.getLockUntil() != null) {
            user.setLoginAttempts(0);
            user.setLockUntil(null);
            userRepository.save(user);
        }

        checkUserStatus(user);

        // Track LOGIN activity
        eventPublisher.publishEvent(ActivityLogEvent.builder()
                .actorId(user.getId())
                .entityType(EntityType.USER)
                .entityId(user.getId())
                .action(ActionType.LOGIN)
                .ipAddress(httpRequest.getRemoteAddr())
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build());

        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(contact);
        return buildAuthResponse(userDetails, user);
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (refreshToken == null) {
            throw new CustomAuthenticationException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        if (!jwtUtils.validateToken(refreshToken)) {
            // FIX: Bug3 - Phát hiện multi-tab race condition:
            // Nếu token vừa bị revoke nhưng trong vòng 30s gần đây (tab khác vừa refresh),
            // trả 409 để FE biết lấy token mới từ cookie thay vì logout.
            try {
                String jti = jwtUtils.extractJti(refreshToken);
                if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(REUSED_REFRESH_PREFIX + jti))) {
                    ConflictException conflict = new ConflictException();
                    conflict.setMessage("Token vừa được làm mới bởi phiên khác, vui lòng thử lại");
                    throw conflict;
                }
            } catch (ConflictException e) {
                throw e;
            } catch (Exception ignored) {}
            throw new CustomAuthenticationException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        String username = jwtUtils.extractUsername(refreshToken);
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new CustomAuthenticationException("Không tìm thấy người dùng liên quan đến token"));

        checkUserStatus(user);

        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(username);
        AuthResponse authResponse = buildAuthResponse(userDetails, user);

        // Token rotation: thu hồi refresh token cũ sau khi đã tạo token mới
        try {
            String oldJti = jwtUtils.extractJti(refreshToken);
            jwtUtils.revokeToken(refreshToken);
            // FIX: Bug3 - Đánh dấu jti cũ là "vừa được dùng" trong 30s
            // để các tab khác nhận 409 thay vì 401 (tránh logout nhầm)
            if (oldJti != null) {
                redisTemplate.opsForValue().set(
                        REUSED_REFRESH_PREFIX + oldJti, "used",
                        REUSED_REFRESH_TTL_SECONDS, TimeUnit.SECONDS
                );
            }
        } catch (ConflictException | CustomAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không thể thu hồi refresh token cũ: {}", e.getMessage());
        }

        return authResponse;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(CustomUserDetail userDetails, User user) {
        return AuthResponse.builder()
                .accessToken(jwtUtils.generateAccessToken(userDetails))
                .refreshToken(jwtUtils.generateRefreshToken(userDetails))
                .expiresIn(3600)
                .tokenType("Bearer")
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .systemRole(user.getSystemRole().name())
                .role(user.getRole() != null ? user.getRole().getSlug() : null)
                .build();
    }

    private void checkUserStatus(User user) {
        switch (user.getStatus()) {
            case SUSPENDED         -> throw new CustomAuthenticationException("Tài khoản đã bị khóa");
            case INACTIVE          -> throw new CustomAuthenticationException("Tài khoản tạm thời bị vô hiệu hóa");
            case PENDING_VERIFICATION -> throw new CustomAuthenticationException("Tài khoản chưa được xác thực");
            default -> { /* ACTIVE — OK */ }
        }
    }

    private String generateSmartAvatar(String fullName) {
        if (fullName == null || fullName.isBlank()) return null;
        try {
            return "https://ui-avatars.com/api/?name=" +
                    URLEncoder.encode(fullName, StandardCharsets.UTF_8) +
                    "&background=random&size=200&color=fff";
        } catch (Exception e) {
            return null;
        }
    }
}
