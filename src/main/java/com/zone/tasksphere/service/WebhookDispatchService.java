package com.zone.tasksphere.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.entity.Webhook;
import com.zone.tasksphere.entity.WebhookDeliveryLog;
import com.zone.tasksphere.repository.WebhookDeliveryLogRepository;
import com.zone.tasksphere.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatchService {

    private static final int MAX_FAILURES = 10;
    private static final long RETRY_DELAY_1_MINUTES = 1L;
    private static final long RETRY_DELAY_2_MINUTES = 5L;
    private static final long RETRY_DELAY_3_MINUTES = 30L;

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryLogRepository webhookDeliveryLogRepository;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService retryScheduler =
            Executors.newScheduledThreadPool(2);

    // ════════════════════════════════════════
    // ASYNC DISPATCH
    // ════════════════════════════════════════

    /**
     * Dispatches a webhook event asynchronously to all active webhooks in the project
     * that are subscribed to the given eventType.
     */
    @Async("taskExecutor")
    public void dispatch(UUID projectId, String eventType, Object payload) {
        List<Webhook> webhooks = webhookRepository.findActiveByProjectIdAndEvent(projectId, eventType);
        if (webhooks.isEmpty()) {
            return;
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for event {}: {}", eventType, e.getMessage());
            return;
        }

        for (Webhook webhook : webhooks) {
            sendWithRetry(webhook, eventType, payloadJson, 1);
        }
    }

    // ════════════════════════════════════════
    // SEND WITH RETRY
    // ════════════════════════════════════════

    private void sendWithRetry(Webhook webhook, String eventType, String payloadJson, int attempt) {
        boolean success = false;
        int responseStatus = -1;
        String responseBody = null;

        try {
            String signature = computeHmac(webhook.getSecret(), payloadJson);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(webhook.getUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("X-TaskSphere-Event", eventType)
                    .header("X-TaskSphere-Delivery", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson));

            if (signature != null) {
                reqBuilder.header("X-TaskSphere-Signature", "sha256=" + signature);
            }

            HttpResponse<String> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            responseStatus = resp.statusCode();
            responseBody = resp.body();
            success = responseStatus >= 200 && responseStatus < 300;

        } catch (Exception e) {
            log.warn("Webhook delivery attempt {} failed for {} ({}): {}",
                    attempt, webhook.getId(), eventType, e.getMessage());
        }

        // Log delivery attempt
        saveDeliveryLog(webhook, eventType, payloadJson, responseStatus, responseBody, success, attempt);

        if (success) {
            // Update last triggered and reset failure count
            updateWebhookSuccess(webhook.getId());
            log.info("Webhook {} delivered successfully for event {}", webhook.getId(), eventType);
        } else {
            // Increment failure count
            int newFailureCount = incrementFailureCount(webhook.getId());

            if (newFailureCount >= MAX_FAILURES) {
                log.warn("Webhook {} disabled after {} consecutive failures", webhook.getId(), MAX_FAILURES);
                return; // Auto-disabled, no more retries
            }

            // Schedule retries
            if (attempt == 1) {
                scheduleRetry(webhook, eventType, payloadJson, 2, RETRY_DELAY_1_MINUTES);
            } else if (attempt == 2) {
                scheduleRetry(webhook, eventType, payloadJson, 3, RETRY_DELAY_2_MINUTES);
            } else if (attempt == 3) {
                scheduleRetry(webhook, eventType, payloadJson, 4, RETRY_DELAY_3_MINUTES);
            }
            // No more retries after attempt 4
        }
    }

    private void scheduleRetry(Webhook webhook, String eventType, String payloadJson,
                                int nextAttempt, long delayMinutes) {
        retryScheduler.schedule(
                () -> {
                    // Re-fetch webhook to check if still active
                    webhookRepository.findById(webhook.getId()).ifPresent(w -> {
                        if (w.isActive() && w.getDeletedAt() == null && w.getFailureCount() < MAX_FAILURES) {
                            sendWithRetry(w, eventType, payloadJson, nextAttempt);
                        }
                    });
                },
                delayMinutes,
                TimeUnit.MINUTES
        );
    }

    // ════════════════════════════════════════
    // DB UPDATES (non-transactional, uses separate transaction per call)
    // ════════════════════════════════════════

    @Transactional
    public void updateWebhookSuccess(UUID webhookId) {
        webhookRepository.findById(webhookId).ifPresent(w -> {
            w.setLastTriggeredAt(Instant.now());
            w.setFailureCount(0);
            webhookRepository.save(w);
        });
    }

    @Transactional
    public int incrementFailureCount(UUID webhookId) {
        return webhookRepository.findById(webhookId).map(w -> {
            int newCount = w.getFailureCount() + 1;
            w.setFailureCount(newCount);
            if (newCount >= MAX_FAILURES) {
                w.setActive(false);
                log.warn("Auto-disabled webhook {} due to {} failures", webhookId, newCount);
            }
            webhookRepository.save(w);
            return newCount;
        }).orElse(0);
    }

    @Transactional
    public void saveDeliveryLog(Webhook webhook, String eventType, String requestBody,
                                 int responseStatus, String responseBody,
                                 boolean success, int attemptNumber) {
        WebhookDeliveryLog logEntry = WebhookDeliveryLog.builder()
                .webhook(webhook)
                .eventType(eventType)
                .requestBody(requestBody)
                .responseStatus(responseStatus > 0 ? responseStatus : null)
                .responseBody(responseBody)
                .success(success)
                .attemptNumber(attemptNumber)
                .build();
        webhookDeliveryLogRepository.save(logEntry);
    }

    // ════════════════════════════════════════
    // SCHEDULED CLEANUP
    // ════════════════════════════════════════

    /**
     * Deletes webhook delivery logs older than 30 days at 3:00 AM every day.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanOldDeliveryLogs() {
        Instant cutoff = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        webhookDeliveryLogRepository.deleteByDeliveredAtBefore(cutoff);
        log.info("Cleaned webhook delivery logs older than 30 days");
    }

    // ════════════════════════════════════════
    // HMAC HELPER
    // ════════════════════════════════════════

    private String computeHmac(String secretKey, String payload) {
        if (secretKey == null || secretKey.isEmpty()) return null;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("HMAC computation failed: {}", e.getMessage());
            return null;
        }
    }
}
