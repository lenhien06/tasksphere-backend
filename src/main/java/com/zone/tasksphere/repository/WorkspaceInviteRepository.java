package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.WorkspaceInvite;
import com.zone.tasksphere.entity.enums.InviteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, UUID> {
    Optional<WorkspaceInvite> findByToken(String token);
    Page<WorkspaceInvite> findByWorkspaceIdAndStatus(UUID workspaceId, InviteStatus status, Pageable pageable);
    Optional<WorkspaceInvite> findByWorkspaceIdAndInviteeEmailAndStatus(UUID workspaceId, String email, InviteStatus status);
    List<WorkspaceInvite> findByInviteeEmailAndStatus(String email, InviteStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE WorkspaceInvite w SET w.status = :newStatus " +
            "WHERE w.workspace.id = :workspaceId AND w.status = :currentStatus " +
            "AND w.expiresAt < :now AND w.deletedAt IS NULL")
    int markExpiredInvites(@Param("workspaceId") UUID workspaceId,
                           @Param("now") Instant now,
                           @Param("currentStatus") InviteStatus currentStatus,
                           @Param("newStatus") InviteStatus newStatus);
}
