package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Attachment Response")
public class AttachmentResponse {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "File name", example = "John Doe")
    private String fileName;
    @Schema(description = "File size", example = "10")
    private Long fileSize;
    @Schema(description = "Mime type", example = "TASK")
    private String mimeType;
    @Schema(description = "Download url", example = "https://example.com/image.png")
    private String downloadUrl;
    @Schema(description = "Preview url", example = "https://example.com/image.png")
    private String previewUrl;
    @Schema(description = "Previewable", example = "true")
    private boolean previewable;
    @Schema(description = "Uploaded by", example = "example")
    private UserSummary uploadedBy;
    @Schema(description = "Uploaded at", example = "2023-12-31T23:59:59Z")
    private Instant uploadedAt;

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
