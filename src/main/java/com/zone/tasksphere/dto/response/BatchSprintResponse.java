package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Phản hồi khi gán nhiều task vào sprint */
@Data
@Builder
@Schema(description = "Batch Sprint Response")
public class BatchSprintResponse {
    @Schema(description = "Updated count", example = "2023-12-31T23:59:59Z")
    private int updatedCount;
    @Schema(description = "Failed ids", example = "550e8400-e29b-41d4-a716-446655440000")
    private List<UUID> failedIds;
    @Schema(description = "Message", example = "string")
    private String message;
}
