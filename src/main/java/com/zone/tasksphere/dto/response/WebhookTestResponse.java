package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Webhook Test Response")
public class WebhookTestResponse {
    @Schema(description = "Success", example = "true")
    private boolean success;
    @Schema(description = "Status code", example = "ACTIVE")
    private Integer statusCode;
    @Schema(description = "Response time", example = "2023-12-31T23:59:59Z")
    private Long responseTime;
    @Schema(description = "Message", example = "string")
    private String message;
}
