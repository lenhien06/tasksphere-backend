package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Create Task Request")
public class CreateTaskRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
    @Schema(description = "Title", example = "Item Title")
    private String title;

    /** Markdown content, optional */
    @Schema(description = "Description", example = "Description of the item")
    private String description;

    private TaskType type = TaskType.TASK;

    private TaskPriority priority = TaskPriority.MEDIUM;

    /** UUID của assignee — phải là member của project */
    @Schema(description = "Assignee id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID assigneeId;

    // FIX: FR-14 - phải >= ngày hiện tại (bao gồm cả hôm nay)
    @FutureOrPresent(message = "Hạn chót phải là hôm nay hoặc ngày trong tương lai")
    @Schema(description = "Due date", example = "2023-12-31T23:59:59Z")
    private LocalDate dueDate;

    @Schema(description = "Start date", example = "2023-12-01")
    private LocalDate startDate;

    @Min(value = 1, message = "Story points phải từ 1 đến 100 (SRS: positive integer)")
    @Max(value = 100, message = "Story points tối đa 100")
    @Schema(description = "Story points 1–100 (optional, null = chưa gán)", example = "5")
    private Integer storyPoints;

    @DecimalMin(value = "0.0", message = "Số giờ ước tính không được âm")
    @Schema(description = "Estimated hours", example = "10.5")
    private BigDecimal estimatedHours;

    /** null = backlog */
    @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;

    /** null = root task; có → sub-task (BR-15 max depth=3) */
    @Schema(description = "Parent task id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID parentTaskId;

    /** null = cột đầu tiên của project */
    @Schema(description = "Status column id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID statusColumnId;
}
