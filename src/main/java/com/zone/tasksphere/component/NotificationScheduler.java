package com.zone.tasksphere.component;

import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    /**
     * Chạy mỗi ngày lúc 8:00 sáng.
     * Gửi thông báo cho task sắp đến hạn (24h) và task quá hạn.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendDailyNotifications() {
        log.info("[Scheduler] Running daily notification job...");

        // Task sắp đến hạn trong 24h — TASK_DUE_SOON
        Instant now = Instant.now();
        Instant tomorrow = now.plus(24, ChronoUnit.HOURS);

        try {
            List<com.zone.tasksphere.entity.Task> dueSoonTasks =
                taskRepository.findTasksDueSoon(now, tomorrow);

            log.info("[Scheduler] Found {} tasks due soon", dueSoonTasks.size());
            dueSoonTasks.forEach(task -> {
                try {
                    notificationService.sendTaskDueSoon(task);
                } catch (Exception e) {
                    log.warn("[Scheduler] Failed to notify due-soon task {}: {}", task.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("[Scheduler] Error finding due-soon tasks: {}", e.getMessage());
        }

        // Task quá hạn — TASK_OVERDUE
        try {
            List<com.zone.tasksphere.entity.Task> overdueTasks =
                taskRepository.findOverdueTasksWithAssignee();

            log.info("[Scheduler] Found {} overdue tasks", overdueTasks.size());
            overdueTasks.forEach(task -> {
                try {
                    notificationService.sendTaskOverdue(task);
                } catch (Exception e) {
                    log.warn("[Scheduler] Failed to notify overdue task {}: {}", task.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("[Scheduler] Error finding overdue tasks: {}", e.getMessage());
        }

        log.info("[Scheduler] Daily notification job completed");
    }
}
