package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.NotificationResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.NotificationType;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.NotificationRepository;
import com.zone.tasksphere.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRealtimePublisher notificationRealtimePublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TaskRepository taskRepository;

    private static final String UNREAD_COUNT_KEY = "notif:unread:";
    private static final long UNREAD_CACHE_TTL_MINUTES = 10;

    // ── FIX: P5-BE-05 - CRUD API cho Notification ────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(
            UUID userId, Boolean isRead, NotificationType type, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByRecipientIdFiltered(userId, isRead, type, pageable);
        PageResponse<NotificationResponse> response = PageResponse.fromPage(page.map(NotificationResponse::from));
        // SRS: unreadCount luôn được trả về trong response GET /notifications
        response.setUnreadCount(getUnreadCount(userId));
        return response;
    }

    @Transactional
    public void markAsRead(UUID notifId, UUID userId) {
        Notification notification = notificationRepository.findById(notifId)
            .orElseThrow(() -> new NotFoundException("Notification not found: " + notifId));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new Forbidden("Bạn không có quyền đánh dấu thông báo này");
        }
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
            invalidateUnreadCache(userId);
            afterCommit(() -> notificationRealtimePublisher.publishUnreadCount(userId));
        }
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        // FIX: P5-BE-05 - ATOMIC update
        int count = notificationRepository.markAllAsRead(userId, Instant.now());
        invalidateUnreadCache(userId);
        if (count > 0) {
            afterCommit(() -> notificationRealtimePublisher.publishUnreadCount(userId));
        }
        return count;
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        String key = UNREAD_COUNT_KEY + userId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }
        long count = notificationRepository.countByRecipientIdAndIsReadFalseAndDeletedAtIsNull(userId);
        redisTemplate.opsForValue().set(key, count, UNREAD_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return count;
    }

    @Transactional
    public void deleteNotification(UUID notifId, UUID userId) {
        Notification notification = notificationRepository.findById(notifId)
            .orElseThrow(() -> new NotFoundException("Notification not found: " + notifId));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new Forbidden("Bạn không có quyền xóa thông báo này");
        }
        notification.setDeletedAt(Instant.now());
        notificationRepository.save(notification);
        if (!notification.isRead()) {
            invalidateUnreadCache(userId);
            afterCommit(() -> notificationRealtimePublisher.publishUnreadCount(userId));
        }
    }

    private void invalidateUnreadCache(UUID userId) {
        redisTemplate.delete(UNREAD_COUNT_KEY + userId);
    }

    // ── Generic method (backward-compat) ─────────────────────────────────────

    @Transactional
    public void createNotification(User recipient, NotificationType type, String title, String body,
                                   String entityType, UUID entityId) {
        createAndQueue(recipient, type, title, body, entityType, entityId, null, null, null);
    }

    @Transactional
    public void createNotification(User recipient, NotificationType type, String title, String body,
                                   String entityType, UUID entityId, UUID projectId,
                                   String taskCode, User actor) {
        createAndQueue(recipient, type, title, body, entityType, entityId, projectId, taskCode, actor);
    }

    // ── Task notifications ────────────────────────────────────────────────────

    @Transactional
    public void sendTaskAssigned(Task task, User assignee, User assigner) {
        createAndQueue(
            assignee,
            NotificationType.TASK_ASSIGNED,
            "Bạn được giao task mới",
            String.format("Task %s đã được giao cho bạn bởi %s", task.getTaskCode(), assigner.getFullName()),
            "TASK", task.getId(),
            task.getProject().getId(),
            task.getTaskCode(),
            assigner
        );
    }

    @Transactional
    public void sendTaskStatusChanged(Task task, User watcher, String oldStatus, String newStatus) {
        createAndQueue(
            watcher,
            NotificationType.TASK_STATUS_CHANGED,
            "Trạng thái task đã thay đổi",
            String.format("Task %s đã chuyển từ %s sang %s", task.getTaskCode(), oldStatus, newStatus),
            "TASK", task.getId(),
            task.getProject().getId(),
            task.getTaskCode(),
            null
        );
    }

    @Transactional
    public void sendTaskCommented(Task task, User taskOwner, User commenter) {
        if (taskOwner.getId().equals(commenter.getId())) return;
        createAndQueue(
            taskOwner,
            NotificationType.TASK_COMMENTED,
            "Có bình luận mới trên task của bạn",
            String.format("%s đã bình luận trên task %s", commenter.getFullName(), task.getTaskCode()),
            "TASK", task.getId(),
            task.getProject().getId(),
            task.getTaskCode(),
            commenter
        );
    }

    @Transactional
    public void sendMentionNotification(List<User> mentionedUsers, Task task, Comment comment, User actor) {
        mentionedUsers.stream()
            .filter(u -> !u.getId().equals(actor.getId()))
            .forEach(user -> createAndQueue(
                user,
                NotificationType.TASK_MENTIONED,
                "Bạn được nhắc đến trong bình luận",
                String.format("%s đã nhắc đến bạn trong task %s", actor.getFullName(), task.getTaskCode()),
                "COMMENT", comment.getId(),
                task.getProject().getId(),
                task.getTaskCode(),
                actor
            ));
    }

    @Transactional
    public void sendTaskDueSoon(Task task) {
        if (task.getAssignee() == null) return;
        createAndQueue(
            task.getAssignee(),
            NotificationType.TASK_DUE_SOON,
            "Task sắp đến hạn",
            String.format("Task %s sẽ đến hạn trong 24 giờ tới", task.getTaskCode()),
            "TASK", task.getId(),
            task.getProject().getId(),
            task.getTaskCode(),
            null
        );
    }

    @Transactional
    public void sendTaskOverdue(Task task) {
        if (task.getAssignee() == null) return;
        createAndQueue(
            task.getAssignee(),
            NotificationType.TASK_OVERDUE,
            "Task đã quá hạn",
            String.format("Task %s đã quá hạn xử lý", task.getTaskCode()),
            "TASK", task.getId(),
            task.getProject().getId(),
            task.getTaskCode(),
            null
        );
    }

    // ── Sprint notifications ──────────────────────────────────────────────────

    @Transactional
    public void sendSprintStarted(Sprint sprint, List<User> members, User actor) {
        members.stream()
            .filter(u -> !u.getId().equals(actor.getId()))
            .forEach(user -> createAndQueue(
                user,
                NotificationType.SPRINT_STARTED,
                "Sprint mới đã bắt đầu",
                String.format("Sprint '%s' đã bắt đầu", sprint.getName()),
                "SPRINT", sprint.getId(),
                sprint.getProject().getId(),
                null,
                actor
            ));
    }

    @Transactional
    public void sendSprintCompleted(Sprint sprint, List<User> members, User actor) {
        members.stream()
            .filter(u -> !u.getId().equals(actor.getId()))
            .forEach(user -> createAndQueue(
                user,
                NotificationType.SPRINT_COMPLETED,
                "Sprint đã hoàn thành",
                String.format("Sprint '%s' đã hoàn thành", sprint.getName()),
                "SPRINT", sprint.getId(),
                sprint.getProject().getId(),
                null,
                actor
            ));
    }

    // ── Core helper ───────────────────────────────────────────────────────────

    private void createAndQueue(User recipient, NotificationType type,
                                String title, String body,
                                String entityType, UUID entityId,
                                UUID projectId, String taskCode, User actor) {
        Task taskContext = resolveTaskContext(entityType, entityId);

        Notification notification = Notification.builder()
            .recipient(recipient)
            .type(type)
            .title(title)
            .body(body)
            .entityType(entityType)
            .entityId(entityId)
            .projectId(projectId != null ? projectId : taskContext != null ? taskContext.getProject().getId() : null)
            .taskCode(taskCode != null ? taskCode : taskContext != null ? taskContext.getTaskCode() : null)
            .actorId(actor != null ? actor.getId() : null)
            .actorName(actor != null ? actor.getFullName() : null)
            .actorAvatarUrl(actor != null ? actor.getAvatarUrl() : null)
            .isRead(false)
            .createdAt(Instant.now())
            .build();

        notification = notificationRepository.save(notification);
        invalidateUnreadCache(recipient.getId());

        UUID notificationId = notification.getId();
        UUID recipientId = recipient.getId();
        afterCommit(() -> notificationRealtimePublisher.publishNotificationCreated(notificationId, recipientId));
    }

    private Task resolveTaskContext(String entityType, UUID entityId) {
        if (!"TASK".equalsIgnoreCase(entityType) || entityId == null) {
            return null;
        }
        return taskRepository.findById(entityId).orElse(null);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
