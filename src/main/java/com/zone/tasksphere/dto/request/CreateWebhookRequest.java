package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Create Webhook Request")
public class CreateWebhookRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @NotBlank
    @Schema(description = "Url", example = "https://example.com/image.png")
    private String url;

    @Schema(description = "Secret key", example = "PROJ-123")
    private String secretKey;

    @NotNull
    @NotEmpty
    @Schema(description = "Events", example = "[]")
    private List<String> events;
}
