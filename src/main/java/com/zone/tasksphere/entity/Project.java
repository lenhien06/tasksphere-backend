package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Project entity — top-level container for tasks, sprints and members.
 */
@Entity
@Table(
    name = "projects",
    indexes = {
        @Index(name = "idx_projects_key", columnList = "project_key", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Project extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, unique = true,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String name;

    @NotBlank
    @Size(min = 2, max = 10)
    @Column(name = "project_key", nullable = false, unique = true, updatable = false,
            columnDefinition = "VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String projectKey;

    @Column(columnDefinition = "LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String description;

    @Column(name = "start_date")
    private java.time.Instant startDate;

    @Column(name = "end_date")
    private java.time.Instant endDate;

    @Column(name = "task_counter", nullable = false)
    @Builder.Default
    private Integer taskCounter = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectVisibility visibility = ProjectVisibility.PRIVATE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<ProjectMember> members;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<Task> tasks;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<Sprint> sprints;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectStatusColumn> statusColumns;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<CustomField> customFields;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<ProjectInvite> invites;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<Webhook> webhooks;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private List<ProjectView> views;

    @Version
    @Column(nullable = false)
    private Long version;
}
