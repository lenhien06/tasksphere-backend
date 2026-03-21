package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

/** Phản hồi khi xóa version */
@Data
@Builder
@Schema(description = "Delete Version Response")
public class DeleteVersionResponse {
    @Schema(description = "Message", example = "string")
    private String message;
    @Schema(description = "Tasks moved", example = "1")
    private long tasksMoved;
}
