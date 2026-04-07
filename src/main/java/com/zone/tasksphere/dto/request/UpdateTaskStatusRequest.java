package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Update Task Status Request")
public class UpdateTaskStatusRequest {

    @NotNull(message = "Status không được null")
    @Schema(description = "Status", example = "ACTIVE")
    private TaskStatus status;

    @Schema(description = "Status column id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID statusColumnId;

    /** Optional comment — ghi vào activity log */
    @Schema(description = "Comment", example = "string")
    private String comment;
}
