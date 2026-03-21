package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.ChecklistItem;
import com.zone.tasksphere.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, UUID> {

    List<ChecklistItem> findByTaskIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID taskId);

    long countByTaskIdAndDeletedAtIsNull(UUID taskId);

    long countByTaskIdAndIsCompletedTrueAndDeletedAtIsNull(UUID taskId);

    Optional<ChecklistItem> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT COALESCE(MAX(c.sortOrder), -1) FROM ChecklistItem c WHERE c.task.id = :taskId AND c.deletedAt IS NULL")
    int findMaxSortOrderByTaskId(@Param("taskId") UUID taskId);

    List<ChecklistItem> findByIdInAndTaskIdAndDeletedAtIsNull(List<UUID> ids, UUID taskId);

    /** @deprecated use findByTaskIdAndDeletedAtIsNullOrderBySortOrderAsc */
    @Deprecated
    List<ChecklistItem> findByTaskOrderBySortOrderAsc(Task task);
}
