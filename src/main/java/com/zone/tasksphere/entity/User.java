package com.zone.tasksphere.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zone.tasksphere.entity.enums.AuthProvider;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.List;

/**
 * Core user entity with full authentication and profile fields.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
    }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @NotBlank
    @Email
    @Column(nullable = false, unique = true,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String email;

    @JsonIgnore
    @Column(name = "password_hash",
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String passwordHash;

    @NotBlank
    @Size(min = 2, max = 100)
    @Column(name = "full_name", nullable = false,
            columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String fullName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false)
    @Builder.Default
    private SystemRole systemRole = SystemRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "require_password_change", nullable = false)
    @Builder.Default
    private boolean requirePasswordChange = false;

    @Column(name = "login_attempts", nullable = false)
    @Builder.Default
    private int loginAttempts = 0;

    @Column(name = "lock_until")
    private Instant lockUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "email_daily_digest", nullable = false)
    @Builder.Default
    private boolean emailDailyDigest = true;

    @Column(name = "weekdays_only", nullable = false)
    @Builder.Default
    private boolean weekdaysOnly = false;

    // Keep legacy role reference for RBAC compatibility
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    @JsonIgnore
    private Role role;

    // Relationships
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<RefreshToken> refreshTokens;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ProjectMember> projectMemberships;

    @OneToMany(mappedBy = "assignee", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Task> assignedTasks;
}
