package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.dto.request.CreateWebhookRequest;
import com.zone.tasksphere.dto.request.UpdateWebhookRequest;
import com.zone.tasksphere.dto.response.WebhookResponse;
import com.zone.tasksphere.dto.response.WebhookTestResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.Webhook;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.repository.WebhookRepository;
import com.zone.tasksphere.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WebhookServiceImpl implements WebhookService {

    private final WebhookRepository webhookRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // ════════════════════════════════════════
    // CREATE
    // ════════════════════════════════════════

    @Override
    public WebhookResponse createWebhook(UUID projectId, CreateWebhookRequest request, UUID currentUserId) {
        requirePM(projectId, currentUserId);

        // WHK_001 & WHK_002: validate URL
        validateUrl(request.getUrl());

        // WHK_003: max 5 webhooks per project
        long count = webhookRepository.countByProjectIdAndDeletedAtIsNull(projectId);
        if (count >= 5) {
            throw new BusinessRuleException("WHK_003: Tối đa 5 webhooks mỗi dự án");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

        String eventsJson;
        try {
            eventsJson = objectMapper.writeValueAsString(request.getEvents());
        } catch (Exception e) {
            throw new BadRequestException("Danh sách events không hợp lệ");
        }

        String secret = (request.getSecretKey() != null && !request.getSecretKey().isBlank())
                ? request.getSecretKey()
                : "";

        Webhook webhook = Webhook.builder()
                .project(project)
                .createdBy(creator)
                .name(request.getName())
                .url(request.getUrl())
                .secret(secret)
                .events(eventsJson)
                .isActive(true)
                .failureCount(0)
                .build();

        webhook = webhookRepository.save(webhook);
        log.info("Webhook created: {} for project {}", webhook.getId(), projectId);

        return toResponse(webhook);
    }

    // ════════════════════════════════════════
    // LIST
    // ════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<WebhookResponse> getWebhooks(UUID projectId, UUID currentUserId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }

        return webhookRepository.findByProjectIdAndDeletedAtIsNull(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ════════════════════════════════════════
    // UPDATE
    // ════════════════════════════════════════

    @Override
    public WebhookResponse updateWebhook(UUID webhookId, UpdateWebhookRequest request, UUID currentUserId) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new NotFoundException("Webhook không tồn tại"));

        requirePM(webhook.getProject().getId(), currentUserId);

        if (request.getName() != null && !request.getName().isBlank()) {
            webhook.setName(request.getName());
        }

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            validateUrl(request.getUrl());
            webhook.setUrl(request.getUrl());
        }

        if (request.getSecretKey() != null) {
            webhook.setSecret(request.getSecretKey());
        }

        if (request.getEvents() != null && !request.getEvents().isEmpty()) {
            try {
                webhook.setEvents(objectMapper.writeValueAsString(request.getEvents()));
            } catch (Exception e) {
                throw new BadRequestException("Danh sách events không hợp lệ");
            }
        }

        if (request.getActive() != null) {
            webhook.setActive(request.getActive());
        }

        webhook = webhookRepository.save(webhook);
        log.info("Webhook updated: {}", webhookId);

        return toResponse(webhook);
    }

    // ════════════════════════════════════════
    // DELETE
    // ════════════════════════════════════════

    @Override
    public void deleteWebhook(UUID webhookId, UUID currentUserId) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new NotFoundException("Webhook không tồn tại"));

        requirePM(webhook.getProject().getId(), currentUserId);

        webhook.setDeletedAt(Instant.now());
        webhookRepository.save(webhook);
        log.info("Webhook soft-deleted: {}", webhookId);
    }

    // ════════════════════════════════════════
    // TEST
    // ════════════════════════════════════════

    @Override
    public WebhookTestResponse testWebhook(UUID projectId, UUID webhookId, UUID currentUserId) {
        requirePM(projectId, currentUserId);

        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new NotFoundException("Webhook không tồn tại"));

        if (!webhook.getProject().getId().equals(projectId)) {
            throw new Forbidden("Webhook không thuộc dự án này");
        }

        long start = System.currentTimeMillis();
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-TaskSphere-Event", "ping")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            boolean success = resp.statusCode() >= 200 && resp.statusCode() < 300;
            return WebhookTestResponse.builder()
                    .success(success)
                    .statusCode(resp.statusCode())
                    .responseTime(elapsed)
                    .message(success ? "Webhook test thành công" : "Webhook trả về lỗi: " + resp.statusCode())
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return WebhookTestResponse.builder()
                    .success(false)
                    .responseTime(elapsed)
                    .message("Lỗi kết nối: " + e.getMessage())
                    .build();
        }
    }

    // ════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════

    private void requirePM(UUID projectId, UUID userId) {
        var member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
        if (member.getProjectRole() != com.zone.tasksphere.entity.enums.ProjectRole.PROJECT_MANAGER) {
            throw new Forbidden("Chỉ Project Manager mới có quyền thực hiện thao tác này");
        }
    }

    private void validateUrl(String url) {
        try {
            if (!url.startsWith("https://")) {
                throw new BadRequestException("WHK_001: Webhook URL phải dùng HTTPS");
            }
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) throw new BadRequestException("URL không hợp lệ");

            // Block known private hostnames
            if (host.equalsIgnoreCase("localhost") || host.equals("0.0.0.0") || host.equals("::1")) {
                throw new BadRequestException("WHK_002: Không được gửi webhook đến địa chỉ nội bộ");
            }

            // Resolve and check IP
            java.net.InetAddress addr = java.net.InetAddress.getByName(host);
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
                throw new BadRequestException("WHK_002: Không được gửi webhook đến địa chỉ nội bộ");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (java.net.UnknownHostException e) {
            throw new BadRequestException("URL không hợp lệ: không thể resolve hostname");
        } catch (Exception e) {
            throw new BadRequestException("URL không hợp lệ");
        }
    }

    private WebhookResponse toResponse(Webhook webhook) {
        List<String> events = Collections.emptyList();
        if (webhook.getEvents() != null && !webhook.getEvents().isBlank()) {
            try {
                events = objectMapper.readValue(webhook.getEvents(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse events JSON for webhook {}: {}", webhook.getId(), e.getMessage());
            }
        }

        String maskedSecret = (webhook.getSecret() != null && !webhook.getSecret().isEmpty())
                ? "****"
                : null;

        return WebhookResponse.builder()
                .id(webhook.getId())
                .projectId(webhook.getProject().getId())
                .name(webhook.getName())
                .url(webhook.getUrl())
                .secretKey(maskedSecret)
                .events(events)
                .active(webhook.isActive())
                .lastTriggeredAt(webhook.getLastTriggeredAt())
                .failureCount(webhook.getFailureCount())
                .createdAt(webhook.getCreatedAt())
                .build();
    }
}
