package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.ProjectInvite;
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
public interface ProjectInviteRepository extends JpaRepository<ProjectInvite, UUID> {
    Optional<ProjectInvite> findByToken(String token);
    List<ProjectInvite> findByProjectIdAndStatus(UUID projectId, InviteStatus status);
    Page<ProjectInvite> findByProjectIdAndStatus(UUID projectId, InviteStatus status, Pageable pageable);
    List<ProjectInvite> findByProjectId(UUID projectId);
    Optional<ProjectInvite> findByProjectIdAndInviteeEmailAndStatus(UUID projectId, String email, InviteStatus status);
    List<ProjectInvite> findByInviteeEmailAndStatus(String email, InviteStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProjectInvite p SET p.status = :newStatus " +
           "WHERE p.project.id = :projectId AND p.status = :currentStatus " +
           "AND p.expiresAt < :now AND p.deletedAt IS NULL")
    int markExpiredInvites(@Param("projectId") UUID projectId,
                           @Param("now") Instant now,
                           @Param("currentStatus") InviteStatus currentStatus,
                           @Param("newStatus") InviteStatus newStatus);
}
