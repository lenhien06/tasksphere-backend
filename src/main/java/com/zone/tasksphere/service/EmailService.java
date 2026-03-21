package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.DigestContent;
import com.zone.tasksphere.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Email service for transactional emails (OTP, welcome, password reset).
 * All methods are @Async — fire and forget, never block the request thread.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendOtpEmail(String toEmail, String otp) {
        String subject = "Mã xác thực tài khoản TaskSphere của bạn";
        
        Context context = new Context();
        context.setVariable("otp", otp);
        
        String htmlContent = templateEngine.process("emails/otp-email", context);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        String subject = "Chào mừng bạn gia nhập TaskSphere! 🎉";
        
        Context context = new Context();
        context.setVariable("fullName", fullName);
        
        String htmlContent = templateEngine.process("emails/welcome-email", context);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String otp) {
        String subject = "[CẢNH BÁO] Yêu cầu đặt lại mật khẩu TaskSphere";
        
        Context context = new Context();
        context.setVariable("otp", otp);
        
        // Bạn có thể tạo file password-reset-email.html tương tự
        String htmlContent = templateEngine.process("emails/password-reset-email", context);
        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    /**
     * Gửi email lời mời dự án.
     *
     * @param token     Token invite (null nếu user đã có tài khoản và được thêm trực tiếp)
     * @param projectId UUID dự án (dùng để tạo link dashboard cho user đã có tài khoản)
     */
    @Async
    public void sendProjectInviteEmail(String toEmail, String projectName, String inviterName,
                                       String token, UUID projectId) {
        boolean hasInviteToken = (token != null && !token.isEmpty());
        String subject = hasInviteToken
                ? inviterName + " mời bạn tham gia dự án " + projectName + " trên TaskSphere"
                : "Bạn đã được thêm vào dự án " + projectName;

        // Người chưa có TK → trang invite để xem chi tiết rồi đăng ký/đăng nhập & chấp nhận
        // Người đã có TK   → thẳng vào trang dự án
        String inviteLink = hasInviteToken
                ? frontendUrl + "/invite?token=" + token
                : frontendUrl + "/projects/" + projectId;

        Context context = new Context();
        context.setVariable("projectName", projectName);
        context.setVariable("inviterName", inviterName);
        context.setVariable("inviteLink", inviteLink);
        context.setVariable("isNewUser", hasInviteToken);

        try {
            String htmlContent = templateEngine.process("emails/project-invite-email", context);
            sendHtmlEmail(toEmail, subject, htmlContent);
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

        Context context = new Context();
        context.setVariable("projectName", projectName);
        context.setVariable("archiverName", archiverName);
        
        // Cần file project-archived-email.html trong templates/emails/
        try {
            String htmlContent = templateEngine.process("emails/project-archived-email", context);
            sendHtmlEmail(toEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý template email lưu trữ dự án: {}", e.getMessage());
            // Fallback to simple text if template fails
            sendSimpleEmail(toEmail, subject, "Dự án " + projectName + " đã được lưu trữ bởi " + archiverName);
        }
    }

    @Async
    public void sendProjectDeletedEmail(String toEmail, String projectName, String deleterName) {
        String subject = "[CẢNH BÁO] Dự án " + projectName + " đã bị xóa";

        Context context = new Context();
        context.setVariable("projectName", projectName);
        context.setVariable("deleterName", deleterName);

        // Cần file project-deleted-email.html trong templates/emails/
        try {
            String htmlContent = templateEngine.process("emails/project-deleted-email", context);
            sendHtmlEmail(toEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("Lỗi khi xử lý template email xóa dự án: {}", e.getMessage());
            // Fallback to simple text if template fails
            sendSimpleEmail(toEmail, subject, "Dự án " + projectName + " đã bị xóa bởi " + deleterName);
        }
    }

    // ── P6-BE-04: Daily Digest ────────────────────────────────────────────────

    @Async
    public void sendDailyDigest(User user, DigestContent content) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String subject = "📋 TaskSphere — Tóm tắt công việc " + dateStr;

        Context ctx = new Context(new Locale("vi"));
        ctx.setVariable("user", user);
        ctx.setVariable("content", content);
        ctx.setVariable("unsubscribeUrl", frontendUrl + "/settings/notifications?action=unsubscribe");

        try {
            String html = templateEngine.process("emails/daily-digest", ctx);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            helper.setFrom(fromEmail, "TaskSphere");
            mailSender.send(message);
            log.info("[DailyDigest] Đã gửi email tới {}", user.getEmail());
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
            mailSender.send(message);
            log.info("Đã gửi email thành công tới: {}", to);
        } catch (MessagingException e) {
            log.error("Lỗi khi gửi email tới {}: {}", to, e.getMessage());
        }
    }
}
