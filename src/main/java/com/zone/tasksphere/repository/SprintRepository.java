package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.enums.SprintStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, UUID> {

    List<Sprint> findByProjectOrderByStartDateAsc(Project project);

    Optional<Sprint> findByProjectAndStatus(Project project, SprintStatus status);

    /** Tìm sprint theo id + projectId + chưa bị soft-delete */
    Optional<Sprint> findByIdAndProject_IdAndDeletedAtIsNull(UUID id, UUID projectId);

    boolean existsByIdAndProject_IdAndDeletedAtIsNull(UUID id, UUID projectId);

    // ── P4-BE-01: Sprint CRUD queries ──────────────────────────────────

    /** Lấy danh sách sprint theo projectId để sort */
    List<Sprint> findByProject_IdAndDeletedAtIsNull(UUID projectId);

    /** Kiểm tra tên sprint unique trong project */
    boolean existsByProject_IdAndNameAndDeletedAtIsNull(UUID projectId, String name);

    /** Kiểm tra tên unique khi update (exclude chính nó) */
    boolean existsByProject_IdAndNameAndIdNotAndDeletedAtIsNull(UUID projectId, String name, UUID excludeId);

    /** Kiểm tra date overlap với sprint khác trong project (trừ COMPLETED) */
    @Query("""
        SELECT COUNT(s) > 0 FROM Sprint s
        WHERE s.project.id = :projectId
          AND s.deletedAt IS NULL
          AND s.status != com.zone.tasksphere.entity.enums.SprintStatus.COMPLETED
          AND NOT (s.endDate < :startDate OR s.startDate > :endDate)
    """)
    boolean existsOverlappingDates(
            @Param("projectId") UUID projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Kiểm tra date overlap khi update (exclude chính nó) */
    @Query("""
        SELECT COUNT(s) > 0 FROM Sprint s
        WHERE s.project.id = :projectId
          AND s.id != :excludeId
          AND s.deletedAt IS NULL
          AND s.status != com.zone.tasksphere.entity.enums.SprintStatus.COMPLETED
          AND NOT (s.endDate < :startDate OR s.startDate > :endDate)
    """)
    boolean existsOverlappingDatesExclude(
            @Param("projectId") UUID projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") UUID excludeId);

    /** Tổng số task trong sprint */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.sprint.id = :sprintId AND t.deletedAt IS NULL
    """)
    long countTasksBySprintId(@Param("sprintId") UUID sprintId);

    /** Số task DONE trong sprint */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.sprint.id = :sprintId
          AND t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE
          AND t.deletedAt IS NULL
    """)
    long countDoneTasksBySprintId(@Param("sprintId") UUID sprintId);

    // ── P4-BE-02: Sprint Execution queries ─────────────────────────────

    /** Kiểm tra sprint ACTIVE đang tồn tại trong project (SPR_003) */
    Optional<Sprint> findByProject_IdAndStatusAndDeletedAtIsNull(UUID projectId, SprintStatus status);

    /** Lấy task chưa hoàn thành trong sprint */
    @Query("""
        SELECT t FROM Task t
        WHERE t.sprint.id = :sprintId
          AND t.taskStatus NOT IN (
              com.zone.tasksphere.entity.enums.TaskStatus.DONE,
              com.zone.tasksphere.entity.enums.TaskStatus.CANCELLED
          )
          AND t.deletedAt IS NULL
    """)
    List<com.zone.tasksphere.entity.Task> findUnfinishedTasksBySprintId(@Param("sprintId") UUID sprintId);

    /** Tính velocity = SUM storyPoints của task DONE */
    @Query("""
        SELECT COALESCE(SUM(t.storyPoints), 0) FROM Task t
        WHERE t.sprint.id = :sprintId
          AND t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE
          AND t.storyPoints IS NOT NULL
          AND t.deletedAt IS NULL
    """)
    Integer calculateVelocity(@Param("sprintId") UUID sprintId);

    /** Lấy N sprint COMPLETED gần nhất để tính velocity report */
    List<Sprint> findByProject_IdAndStatusAndDeletedAtIsNullOrderByCompletedAtDesc(
            UUID projectId, SprintStatus status, Pageable pageable);

    /** Lấy tên của sprint overlap */
    @Query("""
        SELECT s FROM Sprint s
        WHERE s.project.id = :projectId
          AND s.deletedAt IS NULL
          AND s.status != com.zone.tasksphere.entity.enums.SprintStatus.COMPLETED
          AND NOT (s.endDate < :startDate OR s.startDate > :endDate)
        ORDER BY s.startDate ASC
    """)
    List<Sprint> findOverlappingSprints(
            @Param("projectId") UUID projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Như trên nhưng exclude chính nó (dùng khi update) */
    @Query("""
        SELECT s FROM Sprint s
        WHERE s.project.id = :projectId
          AND s.id != :excludeId
          AND s.deletedAt IS NULL
          AND s.status != com.zone.tasksphere.entity.enums.SprintStatus.COMPLETED
          AND NOT (s.endDate < :startDate OR s.startDate > :endDate)
        ORDER BY s.startDate ASC
    """)
    List<Sprint> findOverlappingSprintsExclude(
            @Param("projectId") UUID projectId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") UUID excludeId);
}
