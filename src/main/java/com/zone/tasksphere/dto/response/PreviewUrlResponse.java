package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Preview Url Response")
public class PreviewUrlResponse {

    @Schema(description = "Preview url", example = "https://example.com/image.png")
    private String previewUrl;
    @Schema(description = "Mime type", example = "TASK")
    private String mimeType;
    @Schema(description = "Expires at", example = "2023-12-31T23:59:59Z")
    private Instant expiresAt;
}
