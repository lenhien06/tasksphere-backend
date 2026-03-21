package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.VersionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

/**
 * Version entity — nhóm các task theo phiên bản phát hành (P4-BE-06).
 */
@Entity
@Table(
    name = "project_versions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_version_project_name",
        columnNames = {"project_id", "name"}
    )
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProjectVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 100,
            columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    @Column(length = 500,
            columnDefinition = "VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VersionStatus status = VersionStatus.PLANNING;

    @Column(name = "release_date")
    private LocalDate releaseDate;
}
