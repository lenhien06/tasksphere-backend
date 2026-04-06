package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.WorkspacePlan;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.util.List;

/**
 * Workspace — lớp tổ chức bao gồm nhiều project con và member pool dùng chung.
 * Project standalone vẫn hoạt động bình thường (workspace_id = NULL trong projects).
 */
@Entity
@Table(
    name = "workspaces",
    indexes = {
        @Index(name = "idx_ws_owner",  columnList = "owner_id"),
        @Index(name = "idx_ws_slug",   columnList = "slug", unique = true)
    }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Workspace extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    /** URL-friendly identifier: 2–50 chars, only a-z / 0-9 / hyphens. UNIQUE. */
    @NotBlank
    @Size(min = 2, max = 50)
    @Column(nullable = false, unique = true,
            columnDefinition = "VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String slug;

    @Column(columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkspacePlan plan = WorkspacePlan.FREE;

    @OneToMany(mappedBy = "workspace", fetch = FetchType.LAZY)
    private List<WorkspaceMember> members;

    @OneToMany(mappedBy = "workspace", fetch = FetchType.LAZY)
    private List<Project> projects;
}
