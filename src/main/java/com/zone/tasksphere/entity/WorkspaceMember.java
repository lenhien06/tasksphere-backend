package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.WorkspaceRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Membership relation between a User and a Workspace.
 * Composite PK: (workspace_id, user_id).
 */
@Entity
@Table(
    name = "workspace_members",
    indexes = {
        @Index(name = "idx_wm_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMember {

    @EmbeddedId
    private WorkspaceMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("workspaceId")
    @JoinColumn(
        name = "workspace_id",
        nullable = false,
        columnDefinition = "CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    )
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(
        name = "user_id",
        nullable = false,
        columnDefinition = "CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;

    /** ["React","TypeScript"] — dùng cho AI scoring. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_tags", columnDefinition = "json")
    private List<String> skillTags;

    @Column(name = "active_task_count", nullable = false)
    @Builder.Default
    private int activeTaskCount = 0;

    @Column(name = "avg_story_points", precision = 4, scale = 1)
    private BigDecimal avgStoryPoints;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "invited_by",
        columnDefinition = "CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    )
    private UUID invitedBy;
}
