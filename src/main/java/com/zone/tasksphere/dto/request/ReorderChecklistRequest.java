package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Reorder Checklist Request")
public class ReorderChecklistRequest {

    @NotNull(message = "Danh sách ID không được null")
    @Schema(description = "Ordered ids", example = "550e8400-e29b-41d4-a716-446655440000")
    private List<UUID> orderedIds;
}
