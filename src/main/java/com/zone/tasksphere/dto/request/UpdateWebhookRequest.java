package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Update Webhook Request")
public class UpdateWebhookRequest {

    @Size(max = 100)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @Schema(description = "Url", example = "https://example.com/image.png")
    private String url;

    @Schema(description = "Secret key", example = "PROJ-123")
    private String secretKey;

    @Schema(description = "Events", example = "[]")
    private List<String> events;

    @Schema(description = "Active", example = "true")
    private Boolean active;
}
