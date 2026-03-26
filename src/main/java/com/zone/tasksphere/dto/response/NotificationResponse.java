package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.Notification;
import com.zone.tasksphere.entity.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Notification Response")
public class NotificationResponse {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Type", example = "TASK")
    private NotificationType type;
    @Schema(description = "Title", example = "Item Title")
    private String title;
    @Schema(description = "Body", example = "string")
    private String body;
    @Schema(description = "Entity type", example = "TASK")
    private String entityType;
    @Schema(description = "Entity id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID entityId;
    @Schema(description = "Project id liên quan", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID projectId;
    @Schema(description = "Task code liên quan", example = "TS-101")
    private String taskCode;
    @Schema(description = "Người thực hiện hành động")
    private ActorSummary actor;
    @Schema(description = "Is read", example = "true")
    private boolean isRead;
    @Schema(description = "Read at", example = "2023-12-31T23:59:59Z")
    private Instant readAt;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;

    public static NotificationResponse from(Notification n) {
        return NotificationResponse.builder()
            .id(n.getId())
            .type(n.getType())
            .title(n.getTitle())
            .body(n.getBody())
            .entityType(n.getEntityType())
            .entityId(n.getEntityId())
            .projectId(n.getProjectId())
            .taskCode(n.getTaskCode())
            .actor(n.getActorId() == null ? null : ActorSummary.builder()
                .id(n.getActorId())
                .fullName(n.getActorName())
                .avatarUrl(n.getActorAvatarUrl())
                .build())
            .isRead(n.isRead())
            .readAt(n.getReadAt())
            .createdAt(n.getCreatedAt())
            .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorSummary {
        private UUID id;
        private String fullName;
        private String avatarUrl;
    }
}
