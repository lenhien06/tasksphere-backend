package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Stores the value of a custom field for a specific task.
 */
@Entity
@Table(
    name = "custom_field_values",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_custom_field_value",
        columnNames = {"task_id", "custom_field_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CustomFieldValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "custom_field_id", nullable = false)
    private CustomField customField;

    @Column(name = "text_value", columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String textValue;

    @Column(name = "number_value", precision = 18, scale = 4)
    private BigDecimal numberValue;

    @Column(name = "date_value")
    private LocalDate dateValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;
}
