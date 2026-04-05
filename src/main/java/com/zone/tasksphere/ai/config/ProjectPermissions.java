package com.zone.tasksphere.ai.config;

import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.security.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Spring Security expression component.
 * Usage: @PreAuthorize("@projectPermissions.isProjectManager(#projectId)")
 */
@Component("projectPermissions")
@RequiredArgsConstructor
public class ProjectPermissions {

    private final ProjectMemberRepository projectMemberRepository;

    public boolean isProjectManager(String projectId) {
        UUID currentUserId = currentUserId();
        UUID pid;
        try { pid = UUID.fromString(projectId); }
        catch (IllegalArgumentException e) { return false; }
        return projectMemberRepository.existsByProject_IdAndUser_IdAndProjectRole(
                pid, currentUserId, ProjectRole.PROJECT_MANAGER);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        CustomUserDetail principal = (CustomUserDetail) auth.getPrincipal();
        return principal.getUserDetail().getId();
    }
}
