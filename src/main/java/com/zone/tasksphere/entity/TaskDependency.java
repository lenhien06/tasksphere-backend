package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.DependencyType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Dependency between tasks: blockingTask must be completed before blockedTask.
 */
@Entity
@Table(
    name = "task_dependencies",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_task_dependency",
        columnNames = {"blocking_task_id", "blocked_task_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TaskDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocking_task_id", nullable = false)
    private Task blockingTask;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_task_id", nullable = false)
    private Task blockedTask;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    @Builder.Default
    private DependencyType linkType = DependencyType.BLOCKS;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
