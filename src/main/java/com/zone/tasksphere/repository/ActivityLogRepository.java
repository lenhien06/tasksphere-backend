package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID>, JpaSpecificationExecutor<ActivityLog> {
    Page<ActivityLog> findByProjectId(UUID projectId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM ActivityLog a WHERE a.action = com.zone.tasksphere.entity.enums.ActionType.LOGIN AND a.createdAt >= :since")
    long countLoginsAfter(@Param("since") Instant since);

    @EntityGraph(attributePaths = {"actor"})
    @Query("""
        SELECT a FROM ActivityLog a
        WHERE a.projectId IN :projectIds
        ORDER BY a.createdAt DESC
    """)
    List<ActivityLog> findRecentByProjectIds(@Param("projectIds") List<UUID> projectIds, Pageable pageable);

    // ── P4-BE-04: Burndown Chart ─────────────────────────────────────

    /**
     * Lấy tổng storyPoints task DONE theo từng ngày trong sprint.
     * Dùng để tính actual line của burndown chart.
     * Kết quả: Object[0] = LocalDate, Object[1] = Long (totalPoints)
     */
    @Query(value = """
        SELECT DATE(a.created_at) as done_date,
               SUM(t.story_points) as points
        FROM activity_logs a
        JOIN tasks t ON t.id = a.entity_id
        WHERE a.entity_type = 'TASK'
          AND a.action_type = 'STATUS_CHANGED'
          AND a.new_values LIKE '%DONE%'
          AND t.sprint_id = :sprintId
          AND t.story_points IS NOT NULL
          AND t.deleted_at IS NULL
          AND a.created_at BETWEEN :startDate AND :endDate
        GROUP BY DATE(a.created_at)
        ORDER BY DATE(a.created_at)
    """, nativeQuery = true)
    List<Object[]> findDonePointsByDate(
            @Param("sprintId") UUID sprintId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    @Query(
        value = """
            SELECT al.* FROM activity_logs al
            WHERE al.project_id = :projectId
              AND (
                    (al.entity_type = 'TASK' AND al.entity_id = :taskId)
                 OR (al.entity_type = 'COMMENT' AND EXISTS (
                        SELECT 1 FROM comments c
                        WHERE c.id = al.entity_id AND c.task_id = :taskId
                    ))
                 OR (al.entity_type = 'ATTACHMENT' AND EXISTS (
                        SELECT 1 FROM attachments a
                        WHERE a.id = al.entity_id AND a.task_id = :taskId
                    ))
                  )
            ORDER BY al.created_at DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM activity_logs al
            WHERE al.project_id = :projectId
              AND (
                    (al.entity_type = 'TASK' AND al.entity_id = :taskId)
                 OR (al.entity_type = 'COMMENT' AND EXISTS (
                        SELECT 1 FROM comments c
                        WHERE c.id = al.entity_id AND c.task_id = :taskId
                    ))
                 OR (al.entity_type = 'ATTACHMENT' AND EXISTS (
                        SELECT 1 FROM attachments a
                        WHERE a.id = al.entity_id AND a.task_id = :taskId
                    ))
                  )
        """,
        nativeQuery = true
    )
    Page<ActivityLog> findTaskActivities(@Param("projectId") String projectId,
                                         @Param("taskId") String taskId,
                                         Pageable pageable);
}
