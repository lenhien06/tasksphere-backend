package com.zone.tasksphere.component;

import com.zone.tasksphere.dto.response.DigestContent;
import com.zone.tasksphere.dto.response.TaskDigestItem;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * P6-BE-04: Gửi email tóm tắt công việc hàng ngày (7:00 thứ 2–6).
 * FR-47: Chỉ gửi khi user có emailDailyDigest=true và status=ACTIVE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DailyDigestJob {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // FR-47: 7:00 sáng thứ 2 đến thứ 6
    @Scheduled(cron = "0 0 7 * * MON-FRI")
    @Transactional(readOnly = true)
    public void sendDailyDigest() {
        log.info("[DailyDigest] Starting at {}", LocalDateTime.now());

        List<User> eligibleUsers = userRepository.findDigestEligibleUsers();
        int sent = 0;

        for (User user : eligibleUsers) {
            try {
                DigestContent content = buildDigestContent(user);
                // FR-47: bỏ qua nếu không có gì để gửi
                if (content.isEmpty()) continue;

                emailService.sendDailyDigest(user, content);
                sent++;
            } catch (Exception e) {
                log.error("[DailyDigest] Lỗi xử lý user {}: {}", user.getEmail(), e.getMessage());
            }
        }

        log.info("[DailyDigest] Hoàn thành — đã gửi cho {} users", sent);
    }

    private DigestContent buildDigestContent(User user) {
        LocalDate today = LocalDate.now();
        Instant since = LocalDateTime.now().minusHours(24).toInstant(ZoneOffset.UTC);

        List<Task> overdue       = taskRepository.findOverdueByAssignee(user.getId(), today);
        List<Task> dueToday      = taskRepository.findDueTodayByAssignee(user.getId(), today);
        List<Task> newlyAssigned = taskRepository.findRecentlyAssigned(user.getId(), since);

        return DigestContent.builder()
                .overdueTasks(toDigestItems(overdue))
                .dueTodayTasks(toDigestItems(dueToday))
                .newlyAssignedTasks(toDigestItems(newlyAssigned))
                .build();
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
                + "/board?taskId=" + task.getId();
    }
}
