package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.SprintStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sprint entity — time-boxed iteration for delivering project tasks.
 */
@Entity
@Table(
    name = "sprints",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_sprint_project_name",
        columnNames = {"project_id", "name"}
    )
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Sprint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false,
            columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    @Column(columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SprintStatus status = SprintStatus.PLANNED;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column
    private Integer velocity;

    @OneToMany(mappedBy = "sprint", fetch = FetchType.LAZY)
    private List<Task> tasks;
}
