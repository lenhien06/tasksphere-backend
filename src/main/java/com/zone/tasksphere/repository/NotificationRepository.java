package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // FIX: P5-BE-05 - Thêm filter theo type
    @Query("""
        SELECT n FROM Notification n
        WHERE n.recipient.id = :userId
          AND n.deletedAt IS NULL
          AND (:isRead IS NULL OR n.isRead = :isRead)
          AND (:type IS NULL OR n.type = :type)
        ORDER BY n.createdAt DESC
    """)
    Page<Notification> findByRecipientIdFiltered(
        @Param("userId") UUID userId,
        @Param("isRead") Boolean isRead,
        @Param("type") com.zone.tasksphere.entity.enums.NotificationType type,
        Pageable pageable);

    long countByRecipientIdAndIsReadFalseAndDeletedAtIsNull(UUID recipientId);

    @Modifying
    @Query("""
        UPDATE Notification n SET n.isRead = true, n.readAt = :readAt
        WHERE n.recipient.id = :userId AND n.isRead = false AND n.deletedAt IS NULL
    """)
    int markAllAsRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
