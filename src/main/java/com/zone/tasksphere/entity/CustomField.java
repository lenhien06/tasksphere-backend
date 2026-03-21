package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.CustomFieldType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * Project-level custom field definition (type, options, display order).
 */
@Entity
@Table(
    name = "custom_fields",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_custom_field_project_name",
        columnNames = {"project_id", "name"}
    )
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CustomField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false,
            columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private CustomFieldType fieldType;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean isRequired = false;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    private boolean isHidden = false;

    /** JSON array of option strings for SELECT and MULTI_SELECT field types. */
    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
