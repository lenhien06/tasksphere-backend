package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Update Task Due Date Request")
public class UpdateTaskDueDateRequest {

    @NotNull(message = "dueDate không được để trống")
    @Schema(description = "Due date (ISO 8601)", example = "2026-04-15")
    private LocalDate dueDate;
}
