package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.AuthResponse;
import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.event.ActivityLogEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.exception.*;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.security.*;
import com.zone.tasksphere.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final WorkspaceService workspaceService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final CaptchaService captchaService;

    private static final String ROLE_USER = "USER";
    private static final String OTP_PREFIX = "auth:otp:";
    private static final long OTP_EXPIRY_MINUTES = 5;
    // FIX: BR-02 - Brute force protection
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    // FIX: Bug3 - Multi-tab race condition: track recently-used refresh tokens
    private static final String REUSED_REFRESH_PREFIX = "reused_refresh:";
    private static final long REUSED_REFRESH_TTL_SECONDS = 120; // 30 → 120s: đủ cho multi-tab race condition
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    @Value("${security.google.client-id:}")
    private String googleClientId;

    // =========================================================
    // 1. THÊM LẠI HÀM GỬI MÃ OTP
    // =========================================================
    @Transactional
    public void sendRegistrationOtp(String email, String turnstileToken, HttpServletRequest httpRequest) {
        validateTurnstileOrThrow(turnstileToken, httpRequest);
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
        validateTurnstileOrThrow(request.getTurnstileToken(), httpServletRequest);

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
            workspaceService.acceptInviteAfterSignup(request.getInviteToken(), newUser);
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
        validateTurnstileOrThrow(request.getTurnstileToken(), httpRequest);

        String contact = request.getEmail().trim();

        User user = userRepository.findByEmail(contact)
                .orElseThrow(() -> new CustomAuthenticationException("Email hoặc mật khẩu không chính xác"));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new CustomAuthenticationException("Tài khoản này sử dụng Google Sign-In. Vui lòng tiếp tục với Google.");
        }

        ensureNotTemporarilyLocked(user);

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
    public AuthResponse loginWithGoogle(String idToken, String turnstileToken, HttpServletRequest httpRequest) {
        validateTurnstileOrThrow(turnstileToken, httpRequest);
        GoogleTokenInfo tokenInfo = verifyGoogleIdToken(idToken);
        String email = tokenInfo.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .map(existing -> syncGoogleUser(existing, tokenInfo))
                .orElseGet(() -> createGoogleUser(tokenInfo));

        ensureNotTemporarilyLocked(user);
        checkUserStatus(user);

        eventPublisher.publishEvent(ActivityLogEvent.builder()
                .actorId(user.getId())
                .entityType(EntityType.USER)
                .entityId(user.getId())
                .action(ActionType.LOGIN)
                .ipAddress(httpRequest.getRemoteAddr())
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build());

        CustomUserDetail userDetails = (CustomUserDetail) userDetailsService.loadUserByUsername(email);
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

    private void ensureNotTemporarilyLocked(User user) {
        if (user.getLockUntil() != null && user.getLockUntil().isAfter(Instant.now())) {
            throw new CustomAuthenticationException(
                    "Tài khoản bị tạm khóa do đăng nhập sai nhiều lần. Thử lại sau " + LOCK_DURATION_MINUTES + " phút.");
        }
    }

    private void validateTurnstileOrThrow(String turnstileToken, HttpServletRequest httpRequest) {
        if (!captchaService.verifyCaptcha(turnstileToken, httpRequest)) {
            throw new BadRequestException("Security verification failed. Please try again.");
        }
    }

    private User syncGoogleUser(User user, GoogleTokenInfo tokenInfo) {
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(tokenInfo.sub())) {
            throw new CustomAuthenticationException("Google account does not match the existing user profile.");
        }

        user.setGoogleSubject(tokenInfo.sub());
        if (user.getAuthProvider() == null) {
            user.setAuthProvider(user.getPasswordHash() == null || user.getPasswordHash().isBlank()
                    ? AuthProvider.GOOGLE
                    : AuthProvider.LOCAL);
        }
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(tokenInfo.picture());
        }
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            user.setFullName(resolveGoogleDisplayName(tokenInfo));
        }
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        return userRepository.save(user);
    }

    private User createGoogleUser(GoogleTokenInfo tokenInfo) {
        Role defaultRole = roleRepository.findBySlug(ROLE_USER)
                .orElseThrow(() -> new BadRequestException("Cấu hình hệ thống lỗi: Role USER chưa được tạo"));

        User newUser = User.builder()
                .fullName(resolveGoogleDisplayName(tokenInfo))
                .email(tokenInfo.email().trim().toLowerCase())
                .passwordHash(null)
                .status(UserStatus.ACTIVE)
                .systemRole(SystemRole.USER)
                .role(defaultRole)
                .avatarUrl(tokenInfo.picture())
                .emailVerifiedAt(Instant.now())
                .authProvider(AuthProvider.GOOGLE)
                .googleSubject(tokenInfo.sub())
                .build();

        return userRepository.save(newUser);
    }

    private String resolveGoogleDisplayName(GoogleTokenInfo tokenInfo) {
        if (tokenInfo.name() != null && !tokenInfo.name().isBlank()) {
            return tokenInfo.name().trim();
        }
        String email = tokenInfo.email() != null ? tokenInfo.email().trim() : "Google User";
        int atIndex = email.indexOf('@');
        String fallback = atIndex > 0 ? email.substring(0, atIndex) : email;
        return fallback.length() >= 2 ? fallback : "Google User";
    }

    private GoogleTokenInfo verifyGoogleIdToken(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new BusinessRuleException("Google Sign-In is not configured on the server.");
        }

        Request request = new Request.Builder()
                .url(GOOGLE_TOKEN_INFO_URL + URLEncoder.encode(idToken, StandardCharsets.UTF_8))
                .get()
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new CustomAuthenticationException("Google token could not be verified.");
            }

            GoogleTokenInfo tokenInfo = objectMapper.readValue(response.body().string(), GoogleTokenInfo.class);
            validateGoogleTokenInfo(tokenInfo);
            return tokenInfo;
        } catch (CustomAuthenticationException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to verify Google token: {}", e.getMessage(), e);
            throw new BusinessRuleException("Google Sign-In is temporarily unavailable. Please try again.");
        }
    }

    private void validateGoogleTokenInfo(GoogleTokenInfo tokenInfo) {
        if (tokenInfo == null) {
            throw new CustomAuthenticationException("Google token payload is invalid.");
        }
        if (tokenInfo.aud() == null || !googleClientId.equals(tokenInfo.aud())) {
            throw new CustomAuthenticationException("Google token audience is invalid.");
        }
        if (tokenInfo.iss() == null || (!"accounts.google.com".equals(tokenInfo.iss()) && !"https://accounts.google.com".equals(tokenInfo.iss()))) {
            throw new CustomAuthenticationException("Google token issuer is invalid.");
        }
        if (!Boolean.parseBoolean(tokenInfo.emailVerified())) {
            throw new CustomAuthenticationException("Google account email is not verified.");
        }
        if (tokenInfo.email() == null || tokenInfo.email().isBlank() || tokenInfo.sub() == null || tokenInfo.sub().isBlank()) {
            throw new CustomAuthenticationException("Google account information is incomplete.");
        }
        try {
            if (tokenInfo.exp() != null && Instant.ofEpochSecond(Long.parseLong(tokenInfo.exp())).isBefore(Instant.now().minus(30, ChronoUnit.SECONDS))) {
                throw new CustomAuthenticationException("Google token has expired.");
            }
        } catch (NumberFormatException e) {
            throw new CustomAuthenticationException("Google token expiry is invalid.");
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

    private record GoogleTokenInfo(
            String sub,
            String email,
            String name,
            String picture,
            String aud,
            String iss,
            String exp,
            @JsonProperty("email_verified") String emailVerified
    ) {
    }
}
