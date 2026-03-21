package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Worklog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorklogRepository extends JpaRepository<Worklog, UUID> {

    List<Worklog> findByTaskIdAndDeletedAtIsNullOrderByLogDateDescCreatedAtDesc(UUID taskId);

    @Query("SELECT COALESCE(SUM(w.timeSpentSeconds), 0) FROM Worklog w WHERE w.task.id = :taskId AND w.deletedAt IS NULL")
    long sumTimeSpentByTaskId(@Param("taskId") UUID taskId);

    /** @deprecated use findByTaskIdAndDeletedAtIsNullOrderByLogDateDescCreatedAtDesc */
    @Deprecated
    List<Worklog> findByTaskId(UUID taskId);
}
