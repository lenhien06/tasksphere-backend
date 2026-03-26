package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import com.zone.tasksphere.entity.enums.VersionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Core task entity with Kanban, sprint, assignment and hierarchy support.
 */
@Entity
@Table(
    name = "tasks",
    indexes = {
        @Index(name = "idx_tasks_code", columnList = "task_code", unique = true),
        @Index(name = "idx_tasks_project", columnList = "project_id"),
        @Index(name = "idx_tasks_assignee", columnList = "assignee_id"),
        @Index(name = "idx_tasks_sprint", columnList = "sprint_id"),
        @Index(name = "idx_tasks_status_column", columnList = "status_column_id"),
        @Index(name = "idx_tasks_parent", columnList = "parent_task_id")
    }
)
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Task extends BaseEntity {

    @NotBlank
    @Column(name = "task_code", nullable = false, unique = true,
            columnDefinition = "VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String taskCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_column_id")
    private ProjectStatusColumn statusColumn;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_status", nullable = false)
    @Builder.Default
    private TaskStatus taskStatus = TaskStatus.TODO;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String title;

    @Column(columnDefinition = "LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskType type = TaskType.TASK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    @Column(name = "story_points")
    private Integer storyPoints;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "completed_at")
    private java.time.Instant completedAt;

    @Column(name = "estimated_hours", precision = 5, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "actual_hours", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal actualHours = java.math.BigDecimal.ZERO;

    @Column(name = "task_position", nullable = false)
    @Builder.Default
    private int taskPosition = 0;

    /** Sub-task depth: 0=root, 1=sub, 2=sub-sub, max=3 (BR-15) */
    @Column(name = "depth", nullable = false)
    @Builder.Default
    private int depth = 0;

    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private boolean isRecurring = false;

    /** P6-BE-02: ID of the recurring template task that generated this instance */
    @Column(name = "parent_recurring_task_id", length = 36)
    private String parentRecurringTaskId;

    /** P4-BE-06: Gán task vào version phát hành */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id")
    private ProjectVersion projectVersion;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "parentTask", fetch = FetchType.LAZY)
    private List<Task> childTasks;

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    private List<Comment> comments;

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    private List<Attachment> attachments;

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    private List<ChecklistItem> checklistItems;

    @OneToMany(mappedBy = "blockedTask", fetch = FetchType.LAZY)
    private List<TaskDependency> blockedBy;

    @OneToMany(mappedBy = "blockingTask", fetch = FetchType.LAZY)
    private List<TaskDependency> blocking;

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    private List<Worklog> worklogs;

    @OneToMany(mappedBy = "task", fetch = FetchType.LAZY)
    private List<CustomFieldValue> customFieldValues;

    @OneToOne(mappedBy = "task", fetch = FetchType.LAZY)
    private RecurringTaskConfig recurringConfig;
}
