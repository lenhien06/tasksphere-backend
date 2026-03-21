package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Time tracking entry logged by a user against a task.
 * Max 86400 seconds (24 hours) per log entry.
 */
@Entity
@Table(name = "worklogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Worklog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Min(1)
    @Max(86400)
    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds;

    @NotNull
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;
}
