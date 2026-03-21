package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.WebhookDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {

    List<WebhookDeliveryLog> findByWebhookIdOrderByDeliveredAtDesc(UUID webhookId);

    @Modifying
    @Query("DELETE FROM WebhookDeliveryLog l WHERE l.deliveredAt < :cutoff")
    void deleteByDeliveredAtBefore(@Param("cutoff") Instant cutoff);
}
