package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    List<ProjectMember> findByProjectId(UUID projectId);
    long countByProjectId(UUID projectId);
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);
    Optional<ProjectMember> findByProjectAndUser(Project project, User user);

    @Query("SELECT pm.project.id AS projectId, COUNT(pm.id) AS memberCount " +
           "FROM ProjectMember pm " +
           "WHERE pm.project.id IN :projectIds " +
           "GROUP BY pm.project.id")
    List<ProjectMemberCountProjection> getProjectMemberCounts(@Param("projectIds") List<UUID> projectIds);

    List<ProjectMember> findByUserIdAndProjectIdIn(UUID userId, List<UUID> projectIds);
    long countByProjectIdAndProjectRole(UUID projectId, ProjectRole role);
    Optional<ProjectMember> findFirstByProjectIdAndProjectRoleOrderByJoinedAtAsc(UUID projectId, ProjectRole role);

    /** Đếm tổng thành viên và số thành viên mới trong 7 ngày qua */
    @Query(value = """
        SELECT
            COUNT(*) AS member_count,
            SUM(CASE WHEN joined_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) AS new_members_last7
        FROM project_members
        WHERE project_id = :projectId
        """, nativeQuery = true)
    Object[] getMemberCountWithNewJoins(@Param("projectId") UUID projectId);

    interface ProjectMemberCountProjection {
        UUID getProjectId();
        Long getMemberCount();
    }

    // ── AI Module queries ─────────────────────────────────────────────────────

    /** Feature 2: PM + MEMBER pool for scoring (excludes VIEWERs). JOIN FETCH user avoids N+1. */
    @Query("""
            SELECT pm FROM ProjectMember pm
            JOIN FETCH pm.user
            WHERE pm.project.id = :projectId
              AND pm.projectRole IN (
                  com.zone.tasksphere.entity.enums.ProjectRole.PROJECT_MANAGER,
                  com.zone.tasksphere.entity.enums.ProjectRole.MEMBER
              )
            """)
    List<ProjectMember> findByProjectIdWithUser(@Param("projectId") UUID projectId);

    /** BR-16: check assignee is a member of this project. */
    boolean existsByProject_IdAndUser_Id(UUID projectId, UUID userId);

    /** ProjectPermissions: check specific role. */
    boolean existsByProject_IdAndUser_IdAndProjectRole(UUID projectId, UUID userId, ProjectRole projectRole);

    /** Needed by confirmAssignments to update active_task_count. */
    Optional<ProjectMember> findByProject_IdAndUser_Id(UUID projectId, UUID userId);
}
