package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional overrides khi promote sub-task thành task độc lập.
 * Trường null = giữ nguyên giá trị hiện tại trên task.
 */
@Data
@Schema(description = "Promote sub-task — chỉnh nhanh title/assignee/due date/description")
public class PromoteSubTaskRequest {

    @Schema(description = "Tiêu đề task sau promote", example = "Triển khai API")
    private String title;

    @Schema(description = "Assignee (member dự án)")
    private UUID assigneeId;

    @Schema(description = "Hạn chót")
    private LocalDate dueDate;

    @Schema(description = "Mô tả (HTML rich text)")
    private String description;
}
