package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateWebhookRequest;
import com.zone.tasksphere.dto.request.UpdateWebhookRequest;
import com.zone.tasksphere.dto.response.WebhookResponse;
import com.zone.tasksphere.dto.response.WebhookTestResponse;

import java.util.List;
import java.util.UUID;

public interface WebhookService {
    WebhookResponse createWebhook(UUID projectId, CreateWebhookRequest request, UUID currentUserId);
    List<WebhookResponse> getWebhooks(UUID projectId, UUID currentUserId);
    WebhookResponse updateWebhook(UUID webhookId, UpdateWebhookRequest request, UUID currentUserId);
    void deleteWebhook(UUID webhookId, UUID currentUserId);
    WebhookTestResponse testWebhook(UUID projectId, UUID webhookId, UUID currentUserId);
}
