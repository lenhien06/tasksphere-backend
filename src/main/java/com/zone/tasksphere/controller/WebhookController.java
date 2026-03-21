package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateWebhookRequest;
import com.zone.tasksphere.dto.request.UpdateWebhookRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.WebhookResponse;
import com.zone.tasksphere.dto.response.WebhookTestResponse;
import com.zone.tasksphere.service.WebhookService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "16. Webhooks", description = "Đăng ký URL nhận thông báo khi có sự kiện trong dự án.")
@SecurityRequirement(name = "bearerAuth")
public class WebhookController {

    private final WebhookService webhookService;

    private UUID getCurrentUserId() {
        var userDetail = AuthUtils.getUserDetail();
        return userDetail != null ? userDetail.getId() : null;
    }

    @Operation(summary = "Đăng ký Webhook mới")
    @PostMapping("/projects/{projectId}/webhooks")
    public ResponseEntity<ApiResponse<WebhookResponse>> createWebhook(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateWebhookRequest request) {
        UUID currentUserId = getCurrentUserId();
        WebhookResponse response = webhookService.createWebhook(projectId, request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách Webhook của dự án")
    @GetMapping("/projects/{projectId}/webhooks")
    public ResponseEntity<ApiResponse<List<WebhookResponse>>> getWebhooks(@PathVariable UUID projectId) {
        UUID currentUserId = getCurrentUserId();
        List<WebhookResponse> webhooks = webhookService.getWebhooks(projectId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(webhooks));
    }

    @Operation(summary = "Cập nhật Webhook")
    @PutMapping("/webhooks/{webhookId}")
    public ResponseEntity<ApiResponse<WebhookResponse>> updateWebhook(
            @PathVariable UUID webhookId,
            @Valid @RequestBody UpdateWebhookRequest request) {
        UUID currentUserId = getCurrentUserId();
        WebhookResponse response = webhookService.updateWebhook(webhookId, request, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Xóa Webhook")
    @DeleteMapping("/webhooks/{webhookId}")
    public ResponseEntity<ApiResponse<Void>> deleteWebhook(@PathVariable UUID webhookId) {
        UUID currentUserId = getCurrentUserId();
        webhookService.deleteWebhook(webhookId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }

    @Operation(summary = "Test Webhook (Gửi payload mẫu)")
    @PostMapping("/projects/{projectId}/webhooks/{webhookId}/test")
    public ResponseEntity<ApiResponse<WebhookTestResponse>> testWebhook(
            @PathVariable UUID projectId,
            @PathVariable UUID webhookId) {
        UUID currentUserId = getCurrentUserId();
        WebhookTestResponse response = webhookService.testWebhook(projectId, webhookId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
