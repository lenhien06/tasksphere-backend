package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Task Filter Params")
public class TaskFilterParams {

    /** Được set bởi controller từ PathVariable */
    @Schema(description = "Project id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID projectId;

    @Schema(description = "Status", example = "ACTIVE")
    private TaskStatus status;

    /** UUID hoặc "me" — service sẽ resolve "me" thành currentUserId */
    @Schema(description = "Assignee id", example = "550e8400-e29b-41d4-a716-446655440000")
    private String assigneeId;

    @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;

    @Schema(description = "Priority", example = "HIGH")
    private TaskPriority priority;

    @Schema(description = "Type", example = "TASK")
    private TaskType type;

    /** Tìm kiếm theo title hoặc taskCode */
    @Schema(description = "Q", example = "string")
    private String q;

    /** Chỉ lấy tasks quá hạn */
    @Schema(description = "Overdue", example = "true")
    private Boolean overdue;

    /** FR-15/FR-27: Chỉ lấy tasks sắp đến hạn (dueDate trong 7 ngày tới, status NOT DONE/CANCELLED) */
    @Schema(description = "Due soon — dueDate in next 7 days, status != DONE/CANCELLED", example = "true")
    private Boolean dueSoon;
}
