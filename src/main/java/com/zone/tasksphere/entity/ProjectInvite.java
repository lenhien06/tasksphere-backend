package com.zone.tasksphere.entity;

import com.zone.tasksphere.entity.enums.InviteStatus;
import com.zone.tasksphere.entity.enums.ProjectRole;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * Invitation record for adding a user to a project.
 */
@Entity
@Table(name = "project_invites")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProjectInvite extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    @Email
    @NotBlank
    @Column(name = "invitee_email", nullable = false,
            columnDefinition = "VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String inviteeEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_user_id")
    private User inviteeUser;

    @NotBlank
    @Column(nullable = false, unique = true,
            columnDefinition = "VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_role", nullable = false)
    @Builder.Default
    private ProjectRole projectRole = ProjectRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InviteStatus status = InviteStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
