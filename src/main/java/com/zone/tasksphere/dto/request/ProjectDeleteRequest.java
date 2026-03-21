package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for delete project with confirmation.
 *
 * Yêu cầu người dùng xác nhận bằng cách nhập chính xác tên hoặc mã dự án.
 * Tăng cường bảo mật tránh xóa nhầm dự án.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Project Delete Request")
public class ProjectDeleteRequest {

    @NotBlank(message = "Tên xác nhận không được để trống")
    @Schema(description = "Confirm name", example = "John Doe")
    private String confirmName;
}

