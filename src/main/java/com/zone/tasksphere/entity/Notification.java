package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.NotificationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * In-app notification sent to a user when relevant events occur.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notif_recipient", columnList = "recipient_id"),
        @Index(name = "idx_notif_is_read", columnList = "is_read"),
        @Index(name = "idx_notif_created", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50,
            columnDefinition = "VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private NotificationType type;

    @NotBlank
    @Column(nullable = false,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String title;

    @Column(columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String body;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entity_id", columnDefinition = "CHAR(36)")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "project_id", columnDefinition = "CHAR(36)")
    private UUID projectId;

    @Column(name = "task_code", length = 50)
    private String taskCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "actor_id", columnDefinition = "CHAR(36)")
    private UUID actorId;

    @Column(name = "actor_name",
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String actorName;

    @Column(name = "actor_avatar_url", length = 1000)
    private String actorAvatarUrl;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "read_at")
    private Instant readAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;
}
