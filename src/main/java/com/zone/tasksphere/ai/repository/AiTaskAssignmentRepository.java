package com.zone.tasksphere.ai.repository;

import com.zone.tasksphere.ai.entity.AiTaskAssignment;
import com.zone.tasksphere.entity.enums.AiAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiTaskAssignmentRepository extends JpaRepository<AiTaskAssignment, UUID> {

    List<AiTaskAssignment> findByTaskIdOrderByTotalScoreDesc(UUID taskId);

    Optional<AiTaskAssignment> findByTaskIdAndSuggestedUserId(UUID taskId, UUID suggestedUserId);

    @Modifying
    @Query("""
            UPDATE AiTaskAssignment a
            SET a.status = :status, a.confirmedBy = :confirmedBy
            WHERE a.id = :id
            """)
    void updateStatus(
            @Param("id") UUID id,
            @Param("status") AiAssignmentStatus status,
            @Param("confirmedBy") UUID confirmedBy);

    @Modifying
    @Query("""
            UPDATE AiTaskAssignment a
            SET a.status = 'OVERRIDDEN', a.confirmedBy = :confirmedBy
            WHERE a.taskId = :taskId AND a.status = 'PENDING'
            """)
    void overrideAllPendingForTask(
            @Param("taskId") UUID taskId,
            @Param("confirmedBy") UUID confirmedBy);
}
