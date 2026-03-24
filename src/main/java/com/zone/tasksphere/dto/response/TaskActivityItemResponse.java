package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Task-scoped activity item")
public class TaskActivityItemResponse {

    private UUID id;
    private String action;
    private String entityType;
    private UUID entityId;
    private Actor actor;
    private String oldValue;
    private String newValue;
    private Instant createdAt;

    @Data
    @Builder
    public static class Actor {
        private UUID id;
        private String fullName;
        private String avatarUrl;
    }
}
