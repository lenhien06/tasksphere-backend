package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "Delete Task Response")
public class DeleteTaskResponse {
    @Schema(description = "Message", example = "string")
    private String message;
    @Schema(description = "Deleted at", example = "2023-12-31T23:59:59Z")
    private Instant deletedAt;
}
