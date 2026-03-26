package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import com.zone.tasksphere.entity.enums.DependencyType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Response cho một dependency record (dùng trong POST response) */
@Data
@Builder
@Schema(description = "Dependency Response")
public class DependencyResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Task", example = "example")
    private TaskRef task;
    @Schema(description = "Depends on task", example = "example")
    private TaskRef dependsOnTask;
    @Schema(description = "Link type from source task perspective", example = "BLOCKS")
    private DependencyType linkType;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;

    @Data
    @Builder
@Schema(description = "Task Ref")
public static class TaskRef {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Task code", example = "CODE-123")
        private String taskCode;
        @Schema(description = "Title", example = "Item Title")
        private String title;
        @Schema(description = "Task status", example = "ACTIVE")
        private TaskStatus taskStatus;
        @Schema(description = "Priority", example = "MEDIUM")
        private TaskPriority priority;
        @Schema(description = "Type", example = "TASK")
        private TaskType type;
    }
}
