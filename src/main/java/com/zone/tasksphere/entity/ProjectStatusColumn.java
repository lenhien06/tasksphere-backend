package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.TaskStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * Custom Kanban column for a project (e.g., To Do, In Progress, Done, Review, Testing).
 */
@Entity
@Table(
    name = "project_status_columns",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_project_column_name",
        columnNames = {"project_id", "name"}
    )
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProjectStatusColumn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false,
            columnDefinition = "VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    @Column(name = "color_hex", length = 7)
    @Builder.Default
    private String colorHex = "#D9D9D9";

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "mapped_status")
    private TaskStatus mappedStatus;
}
