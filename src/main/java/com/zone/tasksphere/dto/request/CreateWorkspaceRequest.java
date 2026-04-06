package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Tạo workspace mới")
public class CreateWorkspaceRequest {

    @NotBlank(message = "Tên workspace không được để trống")
    @Size(max = 255, message = "Tên workspace không được quá 255 ký tự")
    @Schema(description = "Tên workspace", example = "My Team")
    private String name;

    /**
     * Slug tùy chọn — nếu không nhập sẽ được auto-generate từ name.
     * Chỉ chấp nhận: a-z, 0-9, dấu gạch ngang, dài 2-50 ký tự.
     */
    @Size(min = 2, max = 50, message = "Slug phải từ 2-50 ký tự")
    @Schema(description = "URL slug (tùy chọn — tự sinh nếu bỏ trống)", example = "my-team")
    private String slug;

    @Schema(description = "Mô tả workspace (tùy chọn)")
    private String description;
}
