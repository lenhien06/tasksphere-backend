package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Task Digest Item")
public class TaskDigestItem {
    @Schema(description = "Task code", example = "CODE-123")
    private String taskCode;
    @Schema(description = "Title", example = "Item Title")
    private String title;
    @Schema(description = "Priority", example = "HIGH")
    private String priority;
    @Schema(description = "Project name", example = "John Doe")
    private String projectName;
    @Schema(description = "Due date", example = "2023-12-31T23:59:59Z")
    private String dueDate;
    @Schema(description = "Task url", example = "https://example.com/image.png")
    private String taskUrl;
}
