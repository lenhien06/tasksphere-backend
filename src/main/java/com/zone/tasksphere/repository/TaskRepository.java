package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.enums.TaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByTaskCode(String taskCode);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.taskStatus NOT IN (com.zone.tasksphere.entity.enums.TaskStatus.DONE, com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED) AND t.deletedAt IS NULL")
    long countNonTerminalTasks();

    List<Task> findAllByProjectIdOrderByTaskPositionAsc(UUID projectId);

    long countByProjectId(UUID projectId);

    /** Tìm task theo id và projectId (SQLRestriction tự lọc soft-deleted) */
    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);

    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.project.deletedAt IS NULL
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
    """)
    long countAssignedOpenTasks(@Param("userId") UUID userId);

    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.project.deletedAt IS NULL
          AND t.dueDate < :today
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
    """)
    long countOverdueAssignedTasks(@Param("userId") UUID userId,
                                   @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.project.deletedAt IS NULL
          AND t.dueDate = :today
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
    """)
    long countDueTodayAssignedTasks(@Param("userId") UUID userId,
                                    @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.project.deletedAt IS NULL
          AND t.completedAt IS NOT NULL
          AND t.completedAt >= :from
          AND t.completedAt < :to
    """)
    long countCompletedAssignedTasksBetween(@Param("userId") UUID userId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    @EntityGraph(attributePaths = {"project", "assignee"})
    @Query("""
        SELECT t FROM Task t
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.project.deletedAt IS NULL
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
        ORDER BY
          CASE WHEN t.dueDate IS NULL THEN 1 ELSE 0 END,
          t.dueDate ASC,
          t.updatedAt DESC
    """)
    List<Task> findAssignedOpenTasksForDashboard(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "assignee"})
    @Query("""
        SELECT t FROM Task t
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.project.deletedAt IS NULL
          AND t.dueDate IS NOT NULL
          AND t.dueDate >= :today
          AND t.dueDate <= :toDate
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
        ORDER BY t.dueDate ASC, t.updatedAt DESC
    """)
    List<Task> findUpcomingAssignedTasksForDashboard(@Param("userId") UUID userId,
                                                     @Param("today") LocalDate today,
                                                     @Param("toDate") LocalDate toDate,
                                                     Pageable pageable);

    @Query("""
        SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Task t
        WHERE t.deletedAt IS NULL
          AND t.project.deletedAt IS NULL
          AND (
            t.project.owner.id = :userId
            OR EXISTS (
                SELECT 1 FROM ProjectMember pm
                WHERE pm.project = t.project
                  AND pm.user.id = :userId
            )
          )
    """)
    boolean existsWorkspaceTasks(@Param("userId") UUID userId);

    @Query("""
        SELECT DISTINCT t FROM Task t
        LEFT JOIN FETCH t.assignee
        LEFT JOIN FETCH t.parentTask
        WHERE t.project.id = :projectId
          AND t.deletedAt IS NULL
        ORDER BY t.startDate ASC, t.dueDate ASC, t.taskPosition ASC, t.createdAt ASC
    """)
    List<Task> findTimelineTasksByProjectId(@Param("projectId") UUID projectId);

    /** Tìm sub-tasks trực tiếp của task cha (SQLRestriction tự lọc) */
    List<Task> findByParentTaskId(UUID parentTaskId);

    /** Số task trong cột (dùng để tính position mới) */
    long countByStatusColumnId(UUID columnId);

    @Query("SELECT COALESCE(MAX(t.taskPosition), 0) FROM Task t WHERE t.statusColumn.id = :columnId")
    int findMaxPositionByColumnId(@Param("columnId") UUID columnId);

    @Query(value = "SELECT MAX(CAST(SUBSTRING(task_code, CHAR_LENGTH(:projectKey) + 2) AS UNSIGNED)) " +
                   "FROM tasks WHERE project_id = :projectId AND task_code LIKE CONCAT(:projectKey, '-%')",
           nativeQuery = true)
    Optional<Integer> findMaxTaskSequence(@Param("projectId") UUID projectId, @Param("projectKey") String projectKey);

    /** BR-18: Lấy sub-tasks chưa hoàn thành để trả về structured 422 */
    @Query("""
        SELECT t FROM Task t
        WHERE t.parentTask.id = :parentTaskId
          AND t.taskStatus NOT IN :terminalStatuses
    """)
    List<Task> findUnfinishedSubtasks(
        @Param("parentTaskId") UUID parentTaskId,
        @Param("terminalStatuses") List<TaskStatus> terminalStatuses
    );

    /** BR-18: Đếm sub-tasks chưa hoàn thành để kiểm tra trước khi DONE */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.parentTask.id = :parentTaskId
          AND t.taskStatus NOT IN :terminalStatuses
    """)
    long countUnfinishedSubtasks(
        @Param("parentTaskId") UUID parentTaskId,
        @Param("terminalStatuses") List<TaskStatus> terminalStatuses
    );

    /** BR-24: Soft delete đệ quy — cập nhật trực tiếp qua JPQL UPDATE (bỏ qua @SQLRestriction) */
    @Modifying
    @Query("""
        UPDATE Task t SET t.deletedAt = :now
        WHERE t.parentTask.id = :parentId AND t.deletedAt IS NULL
    """)
    void softDeleteDirectSubtasks(@Param("parentId") UUID parentId, @Param("now") Instant now);

    /** Dịch chuyển position để giữ thứ tự khi reorder */
    @Modifying
    @Query("""
        UPDATE Task t SET t.taskPosition = t.taskPosition + 1
        WHERE t.statusColumn.id = :columnId
          AND t.taskPosition >= :fromPosition
          AND t.id != :excludeTaskId
    """)
    void shiftPositionsDown(
        @Param("columnId") UUID columnId,
        @Param("fromPosition") int fromPosition,
        @Param("excludeTaskId") UUID excludeTaskId
    );

    /** Tầng 2: Tìm task chưa gán column — dùng cho migration runner */
    @Query("SELECT t FROM Task t WHERE t.statusColumn IS NULL")
    List<Task> findTasksWithoutStatusColumn();

    /** FIX: FR-12 - Khi member bị xóa khỏi project → unassign tất cả task của họ */
    @Modifying
    @Query("""
        UPDATE Task t SET t.assignee = NULL
        WHERE t.project.id = :projectId AND t.assignee.id = :userId AND t.deletedAt IS NULL
    """)
    void unassignTasksByUserInProject(@Param("projectId") UUID projectId, @Param("userId") UUID userId);

    // ── P3-BE-10: Calendar View ────────────────────────────────────────────────

    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.assignee
        LEFT JOIN FETCH t.statusColumn
        WHERE t.project.id = :projectId
          AND t.deletedAt IS NULL
          AND YEAR(t.dueDate) = :year
          AND MONTH(t.dueDate) = :month
        ORDER BY t.dueDate ASC, t.taskPosition ASC
    """)
    List<Task> findByProjectAndYearAndMonth(
            @Param("projectId") UUID projectId,
            @Param("year") int year,
            @Param("month") int month);

    // ── P3-BE-04: Move tasks to another column (dùng khi xóa cột) ────────────

    @Modifying
    @Query("""
        UPDATE Task t SET t.statusColumn = :newColumn, t.taskStatus = :newStatus
        WHERE t.statusColumn.id = :oldColumnId AND t.deletedAt IS NULL
    """)
    int moveTasksToColumn(
            @Param("oldColumnId") UUID oldColumnId,
            @Param("newColumn") com.zone.tasksphere.entity.ProjectStatusColumn newColumn,
            @Param("newStatus") TaskStatus newStatus);

    @Query("SELECT t.project.id AS projectId, " +
           "COUNT(t.id) AS total, " +
           "SUM(CASE WHEN t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE THEN 1 ELSE 0 END) AS done, " +
           "SUM(CASE WHEN t.taskStatus NOT IN (com.zone.tasksphere.entity.enums.TaskStatus.DONE, com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED) AND (t.dueDate IS NOT NULL AND t.dueDate < CURRENT_DATE) THEN 1 ELSE 0 END) AS overdue " +
           "FROM Task t " +
           "WHERE t.project.id IN :projectIds " +
           "GROUP BY t.project.id")
    List<ProjectTaskStatsProjection> getProjectTaskStats(@Param("projectIds") List<UUID> projectIds);

    interface ProjectTaskStatsProjection {
        UUID getProjectId();
        Long getTotal();
        Long getDone();
        Long getOverdue();
    }

    // ── P4-BE-03: Backlog batch operations ─────────────────────────────

    /** Gán nhiều task vào sprint (atomic) */
    @Modifying
    @Query("""
        UPDATE Task t SET t.sprint.id = :sprintId
        WHERE t.id IN :taskIds
          AND t.project.id = :projectId
          AND t.deletedAt IS NULL
    """)
    int batchAssignToSprint(
            @Param("taskIds") List<UUID> taskIds,
            @Param("projectId") UUID projectId,
            @Param("sprintId") UUID sprintId);

    /** Chuyển nhiều task về backlog (sprint = null) */
    @Modifying
    @Query("""
        UPDATE Task t SET t.sprint = null
        WHERE t.id IN :taskIds
          AND t.project.id = :projectId
          AND t.deletedAt IS NULL
    """)
    int batchMoveToBacklog(
            @Param("taskIds") List<UUID> taskIds,
            @Param("projectId") UUID projectId);

    /** Đếm task trong project thuộc sprint (dùng khi backlog check) */
    long countByProjectIdAndSprintIsNullAndDeletedAtIsNull(UUID projectId);

    /** P4-BE-06: Xóa version khỏi tất cả task khi version bị xóa */
    @Modifying
    @Query("""
        UPDATE Task t SET t.projectVersion = null
        WHERE t.projectVersion.id = :versionId
          AND t.project.id = :projectId
          AND t.deletedAt IS NULL
    """)
    void clearVersionFromTasks(
            @Param("versionId") UUID versionId,
            @Param("projectId") UUID projectId);

    // ── P4-BE-05: Member performance stats ─────────────────────────────

    @Query("""
        SELECT
          t.assignee.id AS assigneeId,
          COUNT(t) AS assigned,
          SUM(CASE WHEN t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE THEN 1 ELSE 0 END) AS done,
          SUM(CASE WHEN t.taskStatus IN (
              com.zone.tasksphere.entity.enums.TaskStatus.IN_PROGRESS,
              com.zone.tasksphere.entity.enums.TaskStatus.IN_REVIEW
          ) THEN 1 ELSE 0 END) AS inProgress,
          SUM(CASE WHEN t.storyPoints IS NOT NULL
               AND t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE
               THEN t.storyPoints ELSE 0 END) AS storyPoints
        FROM Task t
        WHERE t.project.id = :projectId
          AND t.deletedAt IS NULL
          AND (:sprintId IS NULL OR t.sprint.id = :sprintId)
          AND (:dateFrom IS NULL OR t.createdAt >= :dateFrom)
          AND (:dateTo IS NULL OR t.createdAt <= :dateTo)
          AND t.assignee IS NOT NULL
        GROUP BY t.assignee.id
    """)
    List<Object[]> getMemberStats(
            @Param("projectId") UUID projectId,
            @Param("sprintId") UUID sprintId,
            @Param("dateFrom") java.time.Instant dateFrom,
            @Param("dateTo") java.time.Instant dateTo);

    /** Đếm task overdue của assignee trong period */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.project.id = :projectId
          AND t.assignee.id = :assigneeId
          AND t.deletedAt IS NULL
          AND (
            (t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE AND t.dueDate IS NOT NULL
                AND t.updatedAt > FUNCTION('TIMESTAMP', t.dueDate))
            OR (t.taskStatus NOT IN (
                  com.zone.tasksphere.entity.enums.TaskStatus.DONE,
                  com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
               )
               AND t.dueDate IS NOT NULL AND t.dueDate < CURRENT_DATE)
          )
          AND (:sprintId IS NULL OR t.sprint.id = :sprintId)
          AND (:dateFrom IS NULL OR t.createdAt >= :dateFrom)
          AND (:dateTo IS NULL OR t.createdAt <= :dateTo)
    """)
    long countOverdueTasksByAssignee(
            @Param("projectId") UUID projectId,
            @Param("assigneeId") UUID assigneeId,
            @Param("sprintId") UUID sprintId,
            @Param("dateFrom") java.time.Instant dateFrom,
            @Param("dateTo") java.time.Instant dateTo);

    // ── P5-BE-05: Notification Scheduler ───────────────────────────────

    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.assignee
        WHERE t.deletedAt IS NULL
          AND t.assignee IS NOT NULL
          AND t.dueDate IS NOT NULL
          AND t.dueDate > :from
          AND t.dueDate <= :to
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
    """)
    List<Task> findTasksDueSoon(
        @Param("from") java.time.Instant from,
        @Param("to") java.time.Instant to);

    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.assignee
        WHERE t.deletedAt IS NULL
          AND t.assignee IS NOT NULL
          AND t.dueDate IS NOT NULL
          AND t.dueDate < CURRENT_TIMESTAMP
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
    """)
    List<Task> findOverdueTasksWithAssignee();

    // ── P6-BE-02: Recurring Task instances ──────────────────────────────────────

    /** Lấy tất cả task được sinh ra từ template recurring (bao gồm mọi status) */
    @Query("""
        SELECT t FROM Task t
        WHERE t.parentRecurringTaskId = :templateId
          AND t.deletedAt IS NULL
        ORDER BY t.createdAt DESC
    """)
    List<Task> findInstancesByTemplateId(@Param("templateId") String templateId);

    /** Lấy các task instance chưa bắt đầu và chưa gán sprint (dùng để soft-delete khi hủy recurrence) */
    @Query("""
        SELECT t FROM Task t
        WHERE t.parentRecurringTaskId = :templateId
          AND t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.TODO
          AND t.sprint IS NULL
          AND t.deletedAt IS NULL
    """)
    List<Task> findFutureInstancesByTemplateId(@Param("templateId") String templateId);

    // ── P7-BE: Project Overview Stats (single-query native aggregation) ──

    /**
     * Overview stats cho toàn bộ project (không lọc sprint).
     * Trả về [total, done, todo, inProgress, inReview, cancelled, overdue, totalSp, doneSp,
     *         backlog_count, backlog_count_7d_ago, done_count_7d_ago, total_7d_ago]
     */
    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN deleted_at IS NULL THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND task_status = 'DONE'        THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND task_status = 'TODO'        THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND task_status = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND task_status = 'IN_REVIEW'   THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND task_status = 'CANCELLED'   THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND due_date IS NOT NULL AND due_date < CURRENT_DATE
                         AND task_status NOT IN ('DONE', 'CANCELLED') THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL THEN story_points ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND task_status = 'DONE' THEN story_points ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN sprint_id IS NULL AND task_status NOT IN ('DONE','CANCELLED') THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN sprint_id IS NULL AND task_status NOT IN ('DONE','CANCELLED')
                     AND created_at <= DATE_SUB(NOW(), INTERVAL 7 DAY)
                     AND (deleted_at IS NULL OR deleted_at > DATE_SUB(NOW(), INTERVAL 7 DAY))
                     THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN task_status = 'DONE'
                     AND updated_at <= DATE_SUB(NOW(), INTERVAL 7 DAY)
                     AND (deleted_at IS NULL OR deleted_at > DATE_SUB(NOW(), INTERVAL 7 DAY))
                     THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN created_at <= DATE_SUB(NOW(), INTERVAL 7 DAY)
                     AND (deleted_at IS NULL OR deleted_at > DATE_SUB(NOW(), INTERVAL 7 DAY))
                     THEN 1 ELSE 0 END), 0)
        FROM tasks
        WHERE project_id = :projectId
        """, nativeQuery = true)
    List<Object[]> getProjectOverviewWithDeltaStatsAll(@Param("projectId") UUID projectId);

    /**
     * Overview stats cho 1 sprint cụ thể trong project.
     * backlog_count/backlog_count_7d_ago vẫn tính theo backlog toàn project.
     */
    @Query(value = """
        SELECT
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId AND task_status = 'DONE'        THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId AND task_status = 'TODO'        THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId AND task_status = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId AND task_status = 'IN_REVIEW'   THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId AND task_status = 'CANCELLED'   THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN sprint_id = :sprintId
                         AND deleted_at IS NULL
                         AND due_date IS NOT NULL AND due_date < CURRENT_DATE
                         AND task_status NOT IN ('DONE', 'CANCELLED') THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId THEN story_points ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN deleted_at IS NULL AND sprint_id = :sprintId AND task_status = 'DONE'
                              THEN story_points ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN sprint_id IS NULL AND task_status NOT IN ('DONE','CANCELLED') THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN sprint_id IS NULL AND task_status NOT IN ('DONE','CANCELLED')
                     AND created_at <= DATE_SUB(NOW(), INTERVAL 7 DAY)
                     AND (deleted_at IS NULL OR deleted_at > DATE_SUB(NOW(), INTERVAL 7 DAY))
                     THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN sprint_id = :sprintId AND task_status = 'DONE'
                     AND updated_at <= DATE_SUB(NOW(), INTERVAL 7 DAY)
                     AND (deleted_at IS NULL OR deleted_at > DATE_SUB(NOW(), INTERVAL 7 DAY))
                     THEN 1 ELSE 0 END), 0),
            COALESCE(SUM(CASE WHEN sprint_id = :sprintId
                     AND created_at <= DATE_SUB(NOW(), INTERVAL 7 DAY)
                     AND (deleted_at IS NULL OR deleted_at > DATE_SUB(NOW(), INTERVAL 7 DAY))
                     THEN 1 ELSE 0 END), 0)
        FROM tasks
        WHERE project_id = :projectId
        """, nativeQuery = true)
    List<Object[]> getProjectOverviewWithDeltaStatsBySprint(
            @Param("projectId") UUID projectId,
            @Param("sprintId") UUID sprintId);

    // Bỏ getProjectOverviewStats và getOverviewDeltaStats cũ


    // ── P6-BE-04: Daily Digest ───────────────────────────────────────────────

    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.project
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.dueDate < :today
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
        ORDER BY t.dueDate ASC
    """)
    List<Task> findOverdueByAssignee(
            @Param("userId") UUID userId,
            @Param("today") java.time.LocalDate today);

    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.project
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.dueDate = :today
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
    """)
    List<Task> findDueTodayByAssignee(
            @Param("userId") UUID userId,
            @Param("today") java.time.LocalDate today);

    @Query("""
        SELECT t FROM Task t
        LEFT JOIN FETCH t.project
        WHERE t.assignee.id = :userId
          AND t.deletedAt IS NULL
          AND t.createdAt >= :since
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
        ORDER BY t.createdAt DESC
    """)
    List<Task> findRecentlyAssigned(
            @Param("userId") UUID userId,
            @Param("since") Instant since);
}
