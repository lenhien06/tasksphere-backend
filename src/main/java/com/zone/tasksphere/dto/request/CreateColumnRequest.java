package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create Column Request")
public class CreateColumnRequest {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @NotBlank
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu không hợp lệ (#RRGGBB)")
    @Schema(description = "Color hex", example = "string")
    private String colorHex;

    @Schema(description = "Mapped status (default: IN_PROGRESS)", example = "IN_PROGRESS")
    private TaskStatus mappedStatus = TaskStatus.IN_PROGRESS;
}
