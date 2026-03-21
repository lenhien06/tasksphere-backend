package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Update Task Position Request")
public class UpdateTaskPositionRequest {

    @NotNull(message = "statusColumnId không được null")
    @Schema(description = "Status column id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID statusColumnId;

    @Min(value = 0, message = "Position không được âm")
    @Schema(description = "New position", example = "1")
    private int newPosition;
}
