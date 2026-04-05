package com.zone.tasksphere.ai.config;

import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.security.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Spring Security expression component.
 * Usage: @PreAuthorize("@projectPermissions.isProjectManager(#projectId)")
 *
 * REQUIRES_NEW ensures this DB query always runs in its own short transaction
 * and never holds a connection open while waiting for a slow LLM call.
 */
@Component("projectPermissions")
@RequiredArgsConstructor
public class ProjectPermissions {

    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
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
