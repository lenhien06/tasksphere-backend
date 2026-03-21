package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Response DTO cho Kanban column — dùng trong GET /api/v1/projects/{id}/columns */
@Data
@Builder
@Schema(description = "Column Response")
public class ColumnResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Color hex", example = "string")
    private String colorHex;
    @Schema(description = "Sort order", example = "1")
    private int sortOrder;
    @Schema(description = "Is default", example = "true")
    private boolean isDefault;
    @Schema(description = "Mapped status", example = "ACTIVE")
    private TaskStatus mappedStatus;
    /** Số lượng task trong cột (không tính soft-deleted) */
    @Schema(description = "Task count", example = "10")
    private int taskCount;
}
