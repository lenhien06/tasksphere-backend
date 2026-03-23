package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Update Custom Field Request")
public class UpdateCustomFieldRequest {

    @Size(max = 100)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @Schema(description = "Options", example = "[]")
    private List<String> options;

    @Schema(description = "Position", example = "1")
    private Integer position;

    @Schema(description = "Required", example = "true")
    private Boolean required;

    /**
     * {@code true} = ẩn field đối với MEMBER/VIEWER khi GET danh sách definitions;
     * PM và System Admin vẫn thấy đầy đủ. Dùng để hiện lại field đã bị ẩn nhầm (đặt {@code false}).
     */
    @Schema(description = "Ẩn field với member (false = hiện lại). PM/Admin luôn thấy mọi field.", example = "false")
    private Boolean hidden;
}
