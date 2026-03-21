package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * Outbound webhook for delivering project events to external URLs.
 * Max 5 webhooks per project — enforced at service layer.
 */
@Entity
@Table(name = "webhooks")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Webhook extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false,
            columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    /** HTTPS URL only — validated at service layer. */
    @NotBlank
    @Column(nullable = false,
            columnDefinition = "VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String url;

    /** HMAC-SHA256 signing key for payload signature. */
    @NotBlank
    @Column(nullable = false,
            columnDefinition = "VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String secret;

    /** JSON array of WebhookEvent enum values. */
    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String events;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private int failureCount = 0;
}
