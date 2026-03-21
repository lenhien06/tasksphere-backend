package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * BR-22: Snapshot of a task's story points at the moment a sprint starts.
 * Velocity is calculated from these snapshots (not the current story points).
 *
 * Table: sprint_task_snapshots — created by Hibernate auto-DDL (ddl-auto=update).
 */
@Entity
@Table(
    name = "sprint_task_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sprint_task_snapshot",
        columnNames = {"sprint_id", "task_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SprintTaskSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    /** Story points recorded at sprint start — used for velocity calculation */
    @Column(name = "story_points_at_start")
    private Integer storyPointsAtStart;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
