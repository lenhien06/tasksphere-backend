package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Saved Filter Response")
public class SavedFilterResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Filter criteria", example = "example")
    private Map<String, Object> filterCriteria;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
}
