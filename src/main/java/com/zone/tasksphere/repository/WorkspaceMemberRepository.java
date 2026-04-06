package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.WorkspaceMember;
import com.zone.tasksphere.entity.WorkspaceMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMemberId> {

    @Query("SELECT wm FROM WorkspaceMember wm JOIN FETCH wm.user WHERE wm.workspace.id = :workspaceId")
    List<WorkspaceMember> findByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("SELECT wm FROM WorkspaceMember wm WHERE wm.workspace.id = :workspaceId AND wm.user.id = :userId")
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId);

    boolean existsByIdWorkspaceIdAndIdUserId(UUID workspaceId, UUID userId);

    @Query("SELECT COUNT(wm) FROM WorkspaceMember wm WHERE wm.workspace.id = :workspaceId")
    int countByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
