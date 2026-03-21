package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    List<Webhook> findByProjectIdAndDeletedAtIsNull(UUID projectId);

    long countByProjectIdAndDeletedAtIsNull(UUID projectId);

    @Query("""
        SELECT w FROM Webhook w
        WHERE w.project.id = :projectId
          AND w.isActive = true
          AND w.deletedAt IS NULL
          AND w.events LIKE CONCAT('%', :eventType, '%')
    """)
    List<Webhook> findActiveByProjectIdAndEvent(
        @Param("projectId") UUID projectId,
        @Param("eventType") String eventType);
}
