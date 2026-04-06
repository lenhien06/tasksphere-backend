package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Workspace;
import com.zone.tasksphere.entity.enums.WorkspaceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findBySlug(String slug);

    List<Workspace> findByOwnerId(UUID ownerId);

    Optional<Workspace> findByOwnerIdAndType(UUID ownerId, WorkspaceType type);

    boolean existsBySlug(String slug);

    /** Tìm tất cả workspace mà user là thành viên (kể cả là owner). */
    @Query("""
        SELECT DISTINCT w FROM Workspace w
        JOIN WorkspaceMember wm ON wm.workspace.id = w.id
        WHERE wm.user.id = :userId AND w.deletedAt IS NULL
        ORDER BY w.updatedAt DESC
        """)
    List<Workspace> findAllByMemberUserId(@Param("userId") UUID userId);

    /** Đếm số project con trong workspace. */
    @Query("SELECT COUNT(p) FROM Project p WHERE p.workspace.id = :workspaceId AND p.deletedAt IS NULL")
    int countProjectsByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
