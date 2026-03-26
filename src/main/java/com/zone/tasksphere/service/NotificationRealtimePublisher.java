package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.NotificationResponse;
import com.zone.tasksphere.dto.response.UnreadCountRealtimeResponse;
import com.zone.tasksphere.entity.Notification;
import com.zone.tasksphere.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRealtimePublisher {

    private static final int MAX_RETRIES = 3;

    private final NotificationRepository notificationRepository;
    private final WebSocketService webSocketService;

    public void publishNotificationCreated(UUID notificationId, UUID recipientId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || notification.getDeletedAt() != null) {
            log.warn("[NotificationRealtime] Notification {} không còn tồn tại để push", notificationId);
            return;
        }

        NotificationResponse payload = NotificationResponse.from(notification);
        sendWithRetry(
            recipientId,
            "/queue/notifications",
            payload,
            "notification.created"
        );

        publishUnreadCount(recipientId);
    }

    public void publishUnreadCount(UUID recipientId) {
        long unreadCount = notificationRepository.countByRecipientIdAndIsReadFalseAndDeletedAtIsNull(recipientId);
        UnreadCountRealtimeResponse payload = UnreadCountRealtimeResponse.builder()
            .unreadCount(unreadCount)
            .updatedAt(Instant.now())
            .build();

        sendWithRetry(
            recipientId,
            "/queue/notifications/unread-count",
            payload,
            "notification.unread_count"
        );
    }

    private void sendWithRetry(UUID recipientId, String destination, Object payload, String eventName) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                webSocketService.sendToUserOrThrow(recipientId, destination, payload);
                if (attempt > 1) {
                    log.info("[NotificationRealtime] Gửi lại thành công event {} tới user {} ở lần {}", eventName, recipientId, attempt);
                }
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("[NotificationRealtime] Gửi event {} tới user {} thất bại lần {}/{}: {}",
                    eventName, recipientId, attempt, MAX_RETRIES, ex.getMessage());
            }
        }

        log.error("[NotificationRealtime] Bỏ cuộc sau {} lần gửi event {} tới user {}", MAX_RETRIES, eventName, recipientId, lastFailure);
    }
}
