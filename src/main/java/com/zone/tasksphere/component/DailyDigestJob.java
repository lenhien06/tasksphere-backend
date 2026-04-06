package com.zone.tasksphere.component;

import com.zone.tasksphere.dto.response.DigestContent;
import com.zone.tasksphere.dto.response.TaskDigestItem;
import com.zone.tasksphere.entity.Notification;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.repository.NotificationRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * P6-BE-04: Gửi email tóm tắt công việc hàng ngày (7:00 thứ 2–6).
 * FR-47: Chỉ gửi khi user có emailDailyDigest=true và status=ACTIVE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyDigestJob {

    private static final int DIGEST_HOUR = 7;
    private static final String DIGEST_SENT_KEY_PREFIX = "digest:daily:sent:";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Quét mỗi giờ; mỗi user sẽ được gửi đúng 1 lần vào 07:00 theo timezone của họ.
    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void sendDailyDigest() {
        log.info("[DailyDigest] Starting at {}", LocalDateTime.now());

        List<User> eligibleUsers = userRepository.findDigestEligibleUsers();
        int sent = 0;

        for (User user : eligibleUsers) {
            try {
                ZoneId zoneId = resolveZone(user);
                ZonedDateTime userNow = ZonedDateTime.now(zoneId);
                if (!shouldSendNow(user, userNow)) {
                    continue;
                }

                String sentKey = buildSentKey(user.getId(), userNow.toLocalDate());
                if (Boolean.TRUE.equals(redisTemplate.hasKey(sentKey))) {
                    continue;
                }

                DigestContent content = buildDigestContent(user, userNow);
                if (content.isEmpty()) continue;

                emailService.sendDailyDigest(user, content);
                redisTemplate.opsForValue().set(sentKey, Boolean.TRUE, 2, TimeUnit.DAYS);
                sent++;
            } catch (Exception e) {
                log.error("[DailyDigest] Lỗi xử lý user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("[DailyDigest] Hoàn thành — đã gửi cho {} users", sent);
    }

    private DigestContent buildDigestContent(User user, ZonedDateTime userNow) {
        LocalDate today = userNow.toLocalDate();
        Instant assignedSince = today.minusDays(1)
            .atTime(DIGEST_HOUR, 0)
            .atZone(userNow.getZone())
            .toInstant();

        List<Task> overdue       = taskRepository.findOverdueByAssignee(user.getId(), today);
        List<Task> dueToday      = taskRepository.findDueTodayByAssignee(user.getId(), today);
        List<Task> newlyAssigned = findRecentlyAssignedTasks(user.getId(), assignedSince);

        return DigestContent.builder()
                .overdueTasks(toDigestItems(overdue))
                .dueTodayTasks(toDigestItems(dueToday))
                .newlyAssignedTasks(toDigestItems(newlyAssigned))
                .build();
    }

    private List<Task> findRecentlyAssignedTasks(UUID userId, Instant since) {
        List<Notification> notifications = notificationRepository.findRecentTaskAssignedNotifications(userId, since);
        if (notifications.isEmpty()) {
            return List.of();
        }

        Map<UUID, Notification> latestByTaskId = new LinkedHashMap<>();
        for (Notification notification : notifications) {
            if (notification.getEntityId() != null) {
                latestByTaskId.putIfAbsent(notification.getEntityId(), notification);
            }
        }

        List<Task> tasks = new ArrayList<>(taskRepository.findAllById(latestByTaskId.keySet()));
        tasks.removeIf(task -> task.getDeletedAt() != null
            || task.getTaskStatus() == null
            || task.getTaskStatus().isTerminal());
        tasks.sort((left, right) -> {
            Instant rightCreated = latestByTaskId.get(right.getId()).getCreatedAt();
            Instant leftCreated = latestByTaskId.get(left.getId()).getCreatedAt();
            return rightCreated.compareTo(leftCreated);
        });
        return tasks;
    }

    private List<TaskDigestItem> toDigestItems(List<Task> tasks) {
        return tasks.stream().map(t -> TaskDigestItem.builder()
                .taskCode(t.getTaskCode())
                .title(t.getTitle())
                .priority(t.getPriority() != null ? t.getPriority().name() : "MEDIUM")
                .projectName(t.getProject() != null ? t.getProject().getName() : "")
                .dueDate(t.getDueDate() != null ? t.getDueDate().format(DATE_FMT) : null)
                .taskUrl(buildTaskUrl(t))
                .build()
        ).toList();
    }

    private String buildTaskUrl(Task task) {
        if (task.getProject() == null) return frontendUrl;
        return frontendUrl + "/projects/" + task.getProject().getId()
                + "/tasks/" + task.getId();
    }

    private ZoneId resolveZone(User user) {
        String timezone = user.getTimezone();
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            log.warn("[DailyDigest] Invalid timezone '{}' for user {}, fallback {}", timezone, user.getEmail(), DEFAULT_ZONE);
            return DEFAULT_ZONE;
        }
    }

    private boolean shouldSendNow(User user, ZonedDateTime userNow) {
        if (userNow.getHour() != DIGEST_HOUR) {
            return false;
        }
        if (user.isWeekdaysOnly()) {
            DayOfWeek day = userNow.getDayOfWeek();
            return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
        }
        return true;
    }

    private String buildSentKey(UUID userId, LocalDate date) {
        return DIGEST_SENT_KEY_PREFIX + userId + ":" + date;
    }
}
