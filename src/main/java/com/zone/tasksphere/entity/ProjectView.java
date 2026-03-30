package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Entity
@Table(
    name = "project_views",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_project_view_project_user",
        columnNames = {"project_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_project_views_user", columnList = "user_id"),
        @Index(name = "idx_project_views_project", columnList = "project_id"),
        @Index(name = "idx_project_views_last_viewed", columnList = "last_viewed_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProjectView extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_viewed_at", nullable = false)
    @Builder.Default
    private Instant lastViewedAt = Instant.now();
}
