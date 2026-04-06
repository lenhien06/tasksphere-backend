package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.DigestContent;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.exception.EmailSendException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Email service for transactional emails (OTP, welcome, password reset). */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class EmailService {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${spring.mail.from:noreply@tasksphere.io.vn}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String otp) {
        sendWithRetry(toEmail, "Mã xác thực tài khoản TaskSphere của bạn", buildOtpHtml(otp));
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        String subject = "Chào mừng bạn gia nhập TaskSphere! 🎉";

        Context context = createEmailContext();
        context.setVariable("fullName", fullName);

        String htmlContent = templateEngine.process("emails/welcome-email", context);
        sendWithRetryAsync(toEmail, subject, htmlContent);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String otp) {
        String subject = "[CẢNH BÁO] Yêu cầu đặt lại mật khẩu TaskSphere";

        Context context = createEmailContext();
        context.setVariable("otp", otp);

        String htmlContent = templateEngine.process("emails/password-reset-email", context);
        sendWithRetryAsync(toEmail, subject, htmlContent);
    }

    /**
     * Gửi email lời mời dự án.
     *
     * @param token     Token invite (null nếu user đã có tài khoản và được thêm trực tiếp)
     * @param projectId UUID dự án (dùng để tạo link dashboard cho user đã có tài khoản)
     */
    @Async
    public void sendProjectInviteEmail(String toEmail, String projectName, String inviterName,
                                       String projectRole, String token, UUID projectId) {
        boolean hasInviteToken = (token != null && !token.isEmpty());
        String subject = hasInviteToken
                ? inviterName + " mời bạn tham gia dự án " + projectName + " trên TaskSphere"
                : "Bạn đã được thêm vào dự án " + projectName;

        // Người chưa có TK → trang invite để xem chi tiết rồi đăng ký/đăng nhập & chấp nhận
        // Người đã có TK   → thẳng vào trang dự án
        String inviteLink = hasInviteToken
                ? frontendUrl + "/invite?token=" + token
                : frontendUrl + "/projects/" + projectId;

        Context context = createEmailContext();
        context.setVariable("projectName", projectName);
        context.setVariable("inviterName", inviterName);
        context.setVariable("projectRole", projectRole);
        context.setVariable("inviteLink", inviteLink);
        context.setVariable("isNewUser", hasInviteToken);

        try {
            String htmlContent = templateEngine.process("emails/project-invite-email", context);
            sendWithRetryAsync(toEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý template email mời dự án: {}", e.getMessage());
            String simpleMessage = hasInviteToken
                    ? "Bạn nhận được lời mời tham gia dự án " + projectName + " từ " + inviterName + ". Xem tại: " + inviteLink
                    : "Bạn đã được thêm vào dự án " + projectName + " bởi " + inviterName + ". Truy cập tại: " + inviteLink;
            sendSimpleEmail(toEmail, subject, simpleMessage);
        }
    }

    @Async
    public void sendProjectArchivedEmail(String toEmail, String projectName, String archiverName) {
        String subject = "[THÔNG BÁO] Dự án " + projectName + " đã được lưu trữ";

        Context context = createEmailContext();
        context.setVariable("projectName", projectName);
        context.setVariable("archiverName", archiverName);

        try {
            String htmlContent = templateEngine.process("emails/project-archived-email", context);
            sendWithRetryAsync(toEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý template email lưu trữ dự án: {}", e.getMessage());
            sendSimpleEmail(toEmail, subject, "Dự án " + projectName + " đã được lưu trữ bởi " + archiverName);
        }
    }

    @Async
    public void sendProjectDeletedEmail(String toEmail, String projectName, String deleterName) {
        String subject = "[CẢNH BÁO] Dự án " + projectName + " đã bị xóa";

        Context context = createEmailContext();
        context.setVariable("projectName", projectName);
        context.setVariable("deleterName", deleterName);

        try {
            String htmlContent = templateEngine.process("emails/project-deleted-email", context);
            sendWithRetryAsync(toEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý template email xóa dự án: {}", e.getMessage());
            sendSimpleEmail(toEmail, subject, "Dự án " + projectName + " đã bị xóa bởi " + deleterName);
        }
    }

    @Async
    public void sendTaskAssignedEmail(User recipient, Task task, User assigner) {
        String subject = String.format("[TaskSphere] Bạn được giao task %s", task.getTaskCode());

        Context context = createEmailContext();
        context.setVariable("recipientName", displayName(recipient));
        context.setVariable("assignerName", displayName(assigner));
        context.setVariable("projectName", task.getProject().getName());
        context.setVariable("taskCode", task.getTaskCode());
        context.setVariable("taskTitle", task.getTitle());
        context.setVariable("taskStatus", task.getTaskStatus() != null ? task.getTaskStatus().name() : "TODO");
        context.setVariable("taskPriority", task.getPriority() != null ? task.getPriority().name() : "MEDIUM");
        context.setVariable("taskUrl", buildTaskUrl(task));
        context.setVariable("dueDate", formatDate(task.getDueDate()));

        try {
            String htmlContent = templateEngine.process("emails/task-assigned-email", context);
            sendWithRetryAsync(recipient.getEmail(), subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý template email giao task: {}", e.getMessage());
            sendSimpleEmail(
                recipient.getEmail(),
                subject,
                String.format("%s đã giao cho bạn task %s - %s. Mở task tại: %s",
                    displayName(assigner), task.getTaskCode(), task.getTitle(), buildTaskUrl(task))
            );
        }
    }

    @Async
    public void sendTaskStatusChangedEmail(User recipient, Task task, String oldStatus, String newStatus, User actor) {
        String subject = String.format("[TaskSphere] Task %s đã chuyển sang %s", task.getTaskCode(), newStatus);

        Context context = createEmailContext();
        context.setVariable("recipientName", displayName(recipient));
        context.setVariable("actorName", displayName(actor));
        context.setVariable("projectName", task.getProject().getName());
        context.setVariable("taskCode", task.getTaskCode());
        context.setVariable("taskTitle", task.getTitle());
        context.setVariable("oldStatus", oldStatus);
        context.setVariable("newStatus", newStatus);
        context.setVariable("taskUrl", buildTaskUrl(task));
        context.setVariable("dueDate", formatDate(task.getDueDate()));

        try {
            String htmlContent = templateEngine.process("emails/task-status-changed-email", context);
            sendWithRetryAsync(recipient.getEmail(), subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý template email đổi trạng thái task: {}", e.getMessage());
            sendSimpleEmail(
                recipient.getEmail(),
                subject,
                String.format("%s đã chuyển task %s - %s từ %s sang %s. Mở task tại: %s",
                    displayName(actor), task.getTaskCode(), task.getTitle(), oldStatus, newStatus, buildTaskUrl(task))
            );
        }
    }

    // ── P6-BE-04: Daily Digest ────────────────────────────────────────────────

    @Async
    public void sendDailyDigest(User user, DigestContent content) {
        String dateStr = LocalDate.now(resolveZone(user.getTimezone()))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String subject = "TaskSphere — Danh sách việc cần làm hôm nay (" + dateStr + ")";

        Context ctx = createEmailContext();
        ctx.setVariable("user", user);
        ctx.setVariable("content", content);
        ctx.setVariable("unsubscribeUrl", frontendUrl + "/settings/notifications?action=unsubscribe");
        ctx.setVariable("digestDate", dateStr);

        try {
            String html = templateEngine.process("emails/daily-digest", ctx);
            sendWithRetryAsync(user.getEmail(), subject, html);
        } catch (Exception e) {
            log.error("[DailyDigest] Lỗi gửi email tới {}: {}", user.getEmail(), e.getMessage());
        }
    }

    private void sendSimpleEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            message.setFrom(fromEmail);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email đơn giản tới {}: {}", to, e.getMessage());
        }
    }

    // ─── Core send helper ─────────────────────────────────────────────────────

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom(fromEmail, "TaskSphere");
            mailSender.send(message);
        } catch (Exception e) {
            throw new EmailSendException("Gửi email thất bại tới " + to, e);
        }
    }

    private void sendWithRetry(String to, String subject, String htmlBody) {
        EmailSendException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("[Email][Attempt {}] Sending to {}", attempt, to);
                sendHtmlEmail(to, subject, htmlBody);
                log.info("[Email] Success to {}", to);
                return;
            } catch (EmailSendException e) {
                lastError = e;
                log.error("[Email][Attempt {}] Failed to {}: {}", attempt, to, e.getMessage());
            }
        }
        throw lastError != null ? lastError : new EmailSendException("Không thể gửi email tới " + to, null);
    }

    @Async
    protected void sendWithRetryAsync(String to, String subject, String htmlBody) {
        sendWithRetry(to, subject, htmlBody);
    }

    private String buildOtpHtml(String otp) {
        Context context = createEmailContext();
        context.setVariable("otp", otp);
        return templateEngine.process("emails/otp-email", context);
    }

    private String buildTaskUrl(Task task) {
        return frontendUrl + "/projects/" + task.getProject().getId() + "/tasks/" + task.getId();
    }

    private String displayName(User user) {
        if (user == null) {
            return "TaskSphere";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getEmail();
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            return DEFAULT_ZONE;
        }
    }

    private Context createEmailContext() {
        Context context = new Context(new Locale("vi"));
        context.setVariable("appName", "TaskSphere");
        context.setVariable("frontendUrl", frontendUrl);
        context.setVariable("logoUrl", frontendUrl + "/images/logo.png");
        context.setVariable("dashboardUrl", frontendUrl + "/dashboard");
        context.setVariable("signInUrl", frontendUrl + "/signin");
        return context;
    }
}
