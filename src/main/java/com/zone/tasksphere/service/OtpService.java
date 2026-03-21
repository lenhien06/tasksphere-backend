package com.zone.tasksphere.service;

import com.zone.tasksphere.entity.EmailOtp;
import com.zone.tasksphere.entity.enums.OtpPurpose;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.EmailOtpRepository;
import com.zone.tasksphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * OTP lifecycle: generate → send → verify.
 * Rate-limited via Redis. OTP stored as BCrypt hash, never plaintext.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int OTP_TTL_SECONDS = 300;       // 5 minutes
    private static final int MAX_ATTEMPTS    = 5;
    private static final int RATE_LIMIT      = 3;          // max per email per hour
    private static final String RATE_KEY_PREFIX = "otp:rate:";

    private final EmailOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Sends an OTP to the given email for the specified purpose.
     * Rate limit: 3 requests per hour per email.
     */
    @Transactional
    public void sendOtp(String email, OtpPurpose purpose) {
        // Rate limit check
        String rateKey = RATE_KEY_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, 3600, TimeUnit.SECONDS);
        }
        if (count != null && count > RATE_LIMIT) {
            throw new BadRequestException("Bạn đã yêu cầu OTP quá nhiều lần. Vui lòng thử lại sau.");
        }

        // Business rule validation
        if (purpose == OtpPurpose.REGISTER && userRepository.existsByEmail(email)) {
            throw new ConflictException("Email này đã được đăng ký");
        }
        if (purpose == OtpPurpose.RESET_PASSWORD) {
            userRepository.findByEmail(email)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản với email này"));
        }

        // Delete old unverified OTPs for this email and purpose
        otpRepository.deleteUnverifiedByEmailAndPurpose(email, purpose);

        // Generate 6-digit OTP
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String otpHash = passwordEncoder.encode(otp);

        otpRepository.save(EmailOtp.builder()
                .email(email)
                .otpHash(otpHash)
                .purpose(purpose)
                .expiredAt(Instant.now().plusSeconds(OTP_TTL_SECONDS))
                .build());

        // Send async email
        if (purpose == OtpPurpose.RESET_PASSWORD) {
            emailService.sendPasswordResetEmail(email, otp);
        } else {
            emailService.sendOtpEmail(email, otp);
        }

        log.info("OTP sent to {} for purpose {}", email, purpose);
    }

    /**
     * Verifies the OTP and marks it as used. Returns the verified OTP record.
     * Throws on invalid, expired, already-used, or max-attempts.
     */
    @Transactional
    public EmailOtp verifyOtp(String email, String otp, OtpPurpose purpose) {
        EmailOtp record = otpRepository.findLatestValid(email, purpose)
                .orElseThrow(() -> new BadRequestException("Mã OTP không hợp lệ hoặc đã hết hạn"));

        if (record.isExpired()) {
            throw new BadRequestException("Mã OTP đã hết hạn");
        }
        if (record.isUsed()) {
            throw new BadRequestException("Mã OTP đã được sử dụng");
        }
        if (record.isExhausted()) {
            otpRepository.delete(record);
            throw new BadRequestException("Bạn đã nhập sai quá nhiều lần. Vui lòng yêu cầu OTP mới");
        }

        if (!passwordEncoder.matches(otp, record.getOtpHash())) {
            record.setAttemptCount(record.getAttemptCount() + 1);
            if (record.isExhausted()) {
                otpRepository.delete(record);
            } else {
                otpRepository.save(record);
            }
            throw new BadRequestException("Mã OTP không đúng");
        }

        // Mark as verified
        record.setVerifiedAt(Instant.now());
        return otpRepository.save(record);
    }
}
