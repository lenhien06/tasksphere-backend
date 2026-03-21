package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Complete Sprint Request")
public class CompleteSprintRequest {

    /** "backlog" hoặc "nextSprint" */
    @NotBlank(message = "unfinishedTasksAction không được để trống")
    @Schema(description = "Unfinished tasks action", example = "string")
    private String unfinishedTasksAction;

    /** Bắt buộc khi action = "nextSprint" */
    @Schema(description = "Next sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID nextSprintId;
}
