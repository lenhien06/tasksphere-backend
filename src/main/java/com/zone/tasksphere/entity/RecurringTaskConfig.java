package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.RecurrenceStatus;
import com.zone.tasksphere.entity.enums.RecurringFrequency;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Configuration for recurring task generation via scheduler.
 */
@Entity
@Table(
    name = "recurring_task_configs",
    indexes = {
        @Index(name = "idx_recurring_next_run", columnList = "next_run_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RecurringTaskConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringFrequency frequency;

    @Column(name = "recurrence_interval", nullable = false)
    @Builder.Default
    private int recurrenceInterval = 1;

    @Column(name = "day_of_week", length = 30)
    private String dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_occurrences", nullable = false)
    @Builder.Default
    private int maxOccurrences = 100;

    @Column(name = "occurrence_count", nullable = false)
    @Builder.Default
    private int occurrenceCount = 0;

    @Column(name = "last_generated_at")
    private Instant lastGeneratedAt;

    @NotNull
    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RecurrenceStatus status = RecurrenceStatus.ACTIVE;

    @Column(name = "frequency_config", columnDefinition = "TEXT")
    private String frequencyConfig;
}
