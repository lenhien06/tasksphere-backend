package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Nâng cấp sub-task thành task độc lập — chỉnh sửa nhanh trước khi promote")
public class PromoteSubTaskRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Tiêu đề task sau khi promote", example = "Triển khai API báo cáo")
    private String title;

    @Schema(description = "Người được giao (null = không gán)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID assigneeId;

    @Schema(description = "Hạn chót", example = "2026-12-31")
    private LocalDate dueDate;

    @Schema(description = "Mô tả (HTML/Markdown-rich từ editor)", example = "<p>Chi tiết...</p>")
    private String description;
}
