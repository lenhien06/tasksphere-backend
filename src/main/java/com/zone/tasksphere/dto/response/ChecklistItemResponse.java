package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Checklist Item Response")
public class ChecklistItemResponse {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Title", example = "Item Title")
    private String title;
    @Schema(description = "Is done", example = "true")
    private boolean isDone;
    @Schema(description = "Sort order", example = "1")
    private int sortOrder;
    @Schema(description = "Completed by", example = "example")
    private UserSummary completedBy;
    @Schema(description = "Completed at", example = "2023-12-31T23:59:59Z")
    private Instant completedAt;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;

    @Data
    @Builder
@Schema(description = "User Summary")
public static class UserSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Full name", example = "John Doe")
        private String fullName;
        @Schema(description = "Avatar url", example = "https://example.com/image.png")
        private String avatarUrl;
    }
}
