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
}
