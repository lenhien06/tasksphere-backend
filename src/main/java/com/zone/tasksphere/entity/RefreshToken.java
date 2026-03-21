package com.zone.tasksphere.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Refresh token entity — stores SHA-256 hash of the token, not plaintext.
 */
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_refresh_token_user", columnList = "user_id"),
        @Index(name = "idx_refresh_token_expired", columnList = "expired_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @JsonIgnore
    @Column(name = "token_hash", nullable = false, unique = true,
            columnDefinition = "VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String tokenHash;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiredAt);
    }
}
