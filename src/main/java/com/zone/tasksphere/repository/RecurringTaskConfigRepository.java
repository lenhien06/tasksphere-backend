package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.RecurringTaskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringTaskConfigRepository extends JpaRepository<RecurringTaskConfig, UUID> {

    Optional<RecurringTaskConfig> findByTaskId(UUID taskId);

    boolean existsByTaskId(UUID taskId);

    /**
     * Find all ACTIVE recurring configs whose next scheduled run is at or before now.
     * We check deletedAt manually since RecurringTaskConfig has no @SQLRestriction.
     */
    @Query("""
        SELECT r FROM RecurringTaskConfig r
        WHERE r.status = com.zone.tasksphere.entity.enums.RecurrenceStatus.ACTIVE
          AND r.nextRunAt <= :now
          AND r.deletedAt IS NULL
    """)
    List<RecurringTaskConfig> findActiveAndDue(@Param("now") Instant now);
}
