package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Phản hồi khi bắt đầu sprint */
@Data
@Builder
@Schema(description = "Sprint Started Response")
public class SprintStartedResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Status", example = "ACTIVE")
    private String status;
    @Schema(description = "Started at", example = "2023-12-31T23:59:59Z")
    private Instant startedAt;
    @Schema(description = "Task count", example = "10")
    private long taskCount;
    @Schema(description = "Message", example = "string")
    private String message;
}
