package com.zone.tasksphere.ai.entity;

import com.zone.tasksphere.entity.enums.AiAssignmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail for AI-generated assignment suggestions (Feature 2).
 * Records are NEVER deleted — status transitions PENDING → CONFIRMED | OVERRIDDEN.
 */
@Entity
@Table(
    name = "ai_task_assignments",
    indexes = {
        @Index(name = "idx_ai_task_score", columnList = "task_id, total_score DESC")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTaskAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "task_id", length = 36, nullable = false, updatable = false)
    private UUID taskId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "suggested_user_id", length = 36, nullable = false, updatable = false)
    private UUID suggestedUserId;

    @Column(name = "skill_score", precision = 4, scale = 3, nullable = false)
    private BigDecimal skillScore;

    @Column(name = "workload_score", precision = 4, scale = 3, nullable = false)
    private BigDecimal workloadScore;

    @Column(name = "difficulty_score", precision = 4, scale = 3, nullable = false)
    private BigDecimal difficultyScore;

    @Column(name = "total_score", precision = 4, scale = 3, nullable = false)
    private BigDecimal totalScore;

    @Column(name = "reason_text", columnDefinition = "TEXT", nullable = false)
    private String reasonText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AiAssignmentStatus status = AiAssignmentStatus.PENDING;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "confirmed_by", length = 36)
    private UUID confirmedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
