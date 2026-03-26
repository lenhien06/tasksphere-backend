package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Update Task Request")
public class UpdateTaskRequest {

    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    @Schema(description = "Title", example = "Item Title")
    private String title;

    @Schema(description = "Description", example = "Description of the item")
    private String description;

    @Schema(description = "Type", example = "TASK")
    private TaskType type;

    @Schema(description = "Priority", example = "HIGH")
    private TaskPriority priority;

    /** null = giữ nguyên assignee hiện tại; "unassign" phải set trống ở tầng service */
    @Schema(description = "Assignee id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID assigneeId;

    @Schema(description = "Due date", example = "2023-12-31T23:59:59Z")
    private LocalDate dueDate;

    @Schema(description = "Start date", example = "2023-12-01")
    private LocalDate startDate;

    @Min(value = 1, message = "Story points phải từ 1 đến 100 (SRS: positive integer)")
    @Max(value = 100, message = "Story points tối đa 100")
    @Schema(description = "Story points 1–100 (optional, null = giữ nguyên hoặc chưa gán)", example = "5")
    private Integer storyPoints;

    @DecimalMin(value = "0.0", message = "Số giờ ước tính không được âm")
    @Schema(description = "Estimated hours", example = "10.5")
    private BigDecimal estimatedHours;

    @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;

    @Schema(description = "Status column id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID statusColumnId;
}
