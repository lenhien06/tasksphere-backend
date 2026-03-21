package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

/**
 * Saved task filter query per project for quick re-use.
 */
@Entity
@Table(name = "saved_filters")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SavedFilter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false,
            columnDefinition = "VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    /** JSON-serialized FilterDTO representing all filter criteria. */
    @NotBlank
    @Column(name = "filter_criteria", nullable = false, columnDefinition = "TEXT")
    private String filterCriteria;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;
}
