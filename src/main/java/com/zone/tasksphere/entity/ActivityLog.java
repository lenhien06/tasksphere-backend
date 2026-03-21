package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Bảng lưu vết lịch sử (Audit Trail).
 * Đặc tính: Bất biến (Read-only/Append-only). 
 * Không có Setter sau khi persist để đảm bảo tính toàn vẹn của Audit Log.
 */
@Entity
@Table(
    name = "activity_logs",
    indexes = {
        @Index(name = "idx_actlog_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_actlog_actor", columnList = "actor_id"),
        @Index(name = "idx_actlog_project", columnList = "project_id"),
        @Index(name = "idx_actlog_created", columnList = "created_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // Để Hibernate sử dụng, không cho init từ ngoài mà thiếu field
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(updatable = false, nullable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /** Người thực hiện hành động. Null nếu là hệ thống (System) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50, updatable = false)
    private EntityType entityType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entity_id", nullable = false, columnDefinition = "CHAR(36)", updatable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50, updatable = false)
    private ActionType action;

    /** 
     * Dữ liệu cũ (JSON String). Ví dụ: {"status": "OPEN"} 
     * Lưu text để không bị giới hạn schema, phục vụ parse diff sau này.
     */
    @Column(name = "old_values", columnDefinition = "TEXT", updatable = false)
    private String oldValues;

    /** Dữ liệu mới (JSON String). Ví dụ: {"status": "IN_PROGRESS"} */
    @Column(name = "new_values", columnDefinition = "TEXT", updatable = false)
    private String newValues;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT", updatable = false)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "project_id", columnDefinition = "CHAR(36)", updatable = false)
    private UUID projectId;
}
