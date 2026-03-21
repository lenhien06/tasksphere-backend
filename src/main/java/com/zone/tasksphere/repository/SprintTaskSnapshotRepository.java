package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.SprintTaskSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SprintTaskSnapshotRepository extends JpaRepository<SprintTaskSnapshot, Long> {

    List<SprintTaskSnapshot> findBySprintId(UUID sprintId);

    /** BR-22: Velocity = SUM storyPointsAtStart cho các task DONE trong sprint */
    @Query("""
        SELECT COALESCE(SUM(s.storyPointsAtStart), 0) FROM SprintTaskSnapshot s
        WHERE s.sprint.id = :sprintId
          AND s.task.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE
          AND s.storyPointsAtStart IS NOT NULL
    """)
    Integer calculateVelocityFromSnapshots(@Param("sprintId") UUID sprintId);

    /** Tổng story points tại thời điểm sprint bắt đầu (dùng cho burndown ideal line) */
    @Query("""
        SELECT COALESCE(SUM(s.storyPointsAtStart), 0) FROM SprintTaskSnapshot s
        WHERE s.sprint.id = :sprintId
          AND s.storyPointsAtStart IS NOT NULL
    """)
    Integer getTotalSnapshotPoints(@Param("sprintId") UUID sprintId);

    boolean existsBySprintId(UUID sprintId);
}
