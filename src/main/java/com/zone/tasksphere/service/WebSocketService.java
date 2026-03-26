package com.zone.tasksphere.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Gửi event đến tất cả subscribers của project (Kanban board, Sprint board).
     * Client subscribe: /topic/project/{projectId}
     */
    public void sendToProject(String projectId, String eventType, Object payload) {
        try {
            Map<String, Object> event = Map.of(
                "type", eventType,
                "data", payload,
                "timestamp", Instant.now().toString()
            );
            messagingTemplate.convertAndSend("/topic/project/" + projectId, event);
            log.debug("[WS] sendToProject: {} → {}", projectId, eventType);
        } catch (Exception e) {
            log.warn("[WS] Failed to send to project {}: {}", projectId, e.getMessage());
        }
    }

    /**
     * Gửi đến 1 user cụ thể (notification, upload status).
     * Client subscribe: /user/queue/notifications
     */
    public void sendToUser(UUID userId, String destination, Object payload) {
        try {
            sendToUserOrThrow(userId, destination, payload);
            log.debug("[WS] sendToUser: {} → {}", userId, destination);
        } catch (Exception e) {
            log.warn("[WS] Failed to send to user {}: {}", userId, e.getMessage());
        }
    }

    public void sendToUserOrThrow(UUID userId, String destination, Object payload) {
        messagingTemplate.convertAndSendToUser(
            userId.toString(),
            destination,
            payload
        );
    }
}
