package com.zone.tasksphere.service;

import com.zone.tasksphere.entity.Notification;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.NotificationType;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.repository.NotificationRepository;
import com.zone.tasksphere.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationRealtimePublisher notificationRealtimePublisher;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    @Test
    void createNotification_persistsAndPublishesRealtimeAfterSave() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        User recipient = new User();
        recipient.setId(userId);

        User actor = new User();
        actor.setId(UUID.randomUUID());
        actor.setFullName("Project Manager");
        actor.setAvatarUrl("https://cdn/avatar.png");

        Task task = new Task();
        task.setId(taskId);
        task.setTaskCode("TS-77");
        com.zone.tasksphere.entity.Project project = new com.zone.tasksphere.entity.Project();
        project.setId(projectId);
        task.setProject(project);

        Notification saved = Notification.builder()
            .id(UUID.randomUUID())
            .recipient(recipient)
            .type(NotificationType.TASK_ASSIGNED)
            .title("Bạn được giao task mới")
            .body("body")
            .entityType("TASK")
            .entityId(taskId)
            .projectId(projectId)
            .taskCode("TS-77")
            .actorId(actor.getId())
            .actorName(actor.getFullName())
            .actorAvatarUrl(actor.getAvatarUrl())
            .createdAt(Instant.now())
            .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        notificationService.createNotification(
            recipient,
            NotificationType.TASK_ASSIGNED,
            "Bạn được giao task mới",
            "body",
            "TASK",
            taskId,
            null,
            null,
            actor
        );

        verify(notificationRepository).save(notificationCaptor.capture());
        Notification toSave = notificationCaptor.getValue();
        assertThat(toSave.getProjectId()).isEqualTo(projectId);
        assertThat(toSave.getTaskCode()).isEqualTo("TS-77");
        assertThat(toSave.getActorId()).isEqualTo(actor.getId());
        verify(redisTemplate).delete("notif:unread:" + userId);
        verify(notificationRealtimePublisher).publishNotificationCreated(saved.getId(), userId);
    }

    @Test
    void createNotification_defersRealtimeUntilAfterCommitWhenTransactionSynchronizationActive() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        User recipient = new User();
        recipient.setId(userId);

        Task task = new Task();
        task.setId(taskId);
        task.setTaskCode("TS-88");
        com.zone.tasksphere.entity.Project project = new com.zone.tasksphere.entity.Project();
        project.setId(projectId);
        task.setProject(project);

        Notification saved = Notification.builder()
            .id(UUID.randomUUID())
            .recipient(recipient)
            .type(NotificationType.TASK_ASSIGNED)
            .title("title")
            .entityType("TASK")
            .entityId(taskId)
            .projectId(projectId)
            .taskCode("TS-88")
            .createdAt(Instant.now())
            .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createNotification(
                recipient,
                NotificationType.TASK_ASSIGNED,
                "title",
                "body",
                "TASK",
                taskId
            );

            verify(notificationRealtimePublisher, never()).publishNotificationCreated(any(UUID.class), any(UUID.class));

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(notificationRealtimePublisher).publishNotificationCreated(saved.getId(), userId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void markAsRead_updatesNotificationAndPushesUnreadCount() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();

        User recipient = new User();
        recipient.setId(userId);

        Notification notification = Notification.builder()
            .id(notifId)
            .recipient(recipient)
            .type(NotificationType.TASK_ASSIGNED)
            .title("title")
            .isRead(false)
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.findById(notifId)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(notifId, userId);

        verify(notificationRepository).save(notification);
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        verify(redisTemplate).delete("notif:unread:" + userId);
        verify(notificationRealtimePublisher).publishUnreadCount(userId);
    }

    @Test
    void deleteNotification_rejectsOtherRecipient() {
        UUID userId = UUID.randomUUID();
        User recipient = new User();
        recipient.setId(UUID.randomUUID());

        Notification notification = Notification.builder()
            .id(UUID.randomUUID())
            .recipient(recipient)
            .type(NotificationType.TASK_ASSIGNED)
            .title("title")
            .createdAt(Instant.now())
            .build();

        when(notificationRepository.findById(notification.getId())).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.deleteNotification(notification.getId(), userId))
            .isInstanceOf(Forbidden.class);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationRealtimePublisher, never()).publishUnreadCount(any(UUID.class));
    }
}
