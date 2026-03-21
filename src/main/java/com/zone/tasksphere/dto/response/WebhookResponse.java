package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Webhook Response")
public class WebhookResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Project id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID projectId;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Url", example = "https://example.com/image.png")
    private String url;
    @Schema(description = "Secret key", example = "PROJ-123")
    private String secretKey;   // masked as "****" if set
    @Schema(description = "Events", example = "[]")
    private List<String> events;
    @Schema(description = "Active", example = "true")
    private boolean active;
    @Schema(description = "Last triggered at", example = "2023-12-31T23:59:59Z")
    private Instant lastTriggeredAt;
    @Schema(description = "Failure count", example = "10")
    private int failureCount;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
}
