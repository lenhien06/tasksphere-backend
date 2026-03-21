package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update Task Status Request")
public class UpdateTaskStatusRequest {

    @NotNull(message = "Status không được null")
    @Schema(description = "Status", example = "ACTIVE")
    private TaskStatus status;

    /** Optional comment — ghi vào activity log */
    @Schema(description = "Comment", example = "string")
    private String comment;
}
