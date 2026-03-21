package com.zone.tasksphere.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zone.tasksphere.entity.enums.OtpPurpose;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Email OTP entity — stores BCrypt hash of the OTP code, never plaintext.
 */
@Entity
@Table(
    name = "email_otps",
    indexes = {
        @Index(name = "idx_email_otps_email", columnList = "email"),
        @Index(name = "idx_email_otps_expired", columnList = "expired_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EmailOtp extends BaseEntity {

    @Email
    @Column(nullable = false,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String email;

    @JsonIgnore
    @Column(name = "otp_hash", nullable = false,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String otpHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OtpPurpose purpose = OtpPurpose.REGISTER;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiredAt);
    }

    public boolean isUsed() {
        return this.verifiedAt != null;
    }

    public boolean isExhausted() {
        return this.attemptCount >= 5;
    }
}
