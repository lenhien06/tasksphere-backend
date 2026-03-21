package com.zone.tasksphere.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

/**
 * System RBAC role entity (separate from project-level roles).
 * Uses Long auto-increment PK for simplicity in permission management.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true, length = 50)
    String slug;

    @Column(name = "display_name", length = 100)
    String displayName;

    @Column(name = "is_active")
    Boolean isActive;

    @Column(name = "is_system")
    Boolean isSystem;

    @Column(length = 255)
    String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "roles_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    Set<Permission> permissions;
}
