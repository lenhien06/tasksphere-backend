package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Delete Column Response")
public class DeleteColumnResponse {
    @Schema(description = "Message", example = "string")
    private String message;
    @Schema(description = "Moved task count", example = "10")
    private int movedTaskCount;
    @Schema(description = "Moved to column", example = "string")
    private String movedToColumn;
}
