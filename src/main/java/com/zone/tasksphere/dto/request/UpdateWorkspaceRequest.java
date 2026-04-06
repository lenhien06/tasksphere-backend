package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Cập nhật workspace")
public class UpdateWorkspaceRequest {

    @NotBlank(message = "Tên workspace không được để trống")
    @Size(max = 255)
    @Schema(description = "Tên workspace", example = "My Team")
    private String name;

    @Schema(description = "Mô tả workspace")
    private String description;

    @Schema(description = "URL avatar của workspace")
    private String avatarUrl;
}
