package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update Column Request")
public class UpdateColumnRequest {

    @Size(max = 50)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Mã màu không hợp lệ (#RRGGBB)")
    @Schema(description = "Color hex", example = "string")
    private String colorHex;
}
