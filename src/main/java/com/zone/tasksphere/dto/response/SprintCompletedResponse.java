package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Phản hồi khi hoàn thành sprint */
@Data
@Builder
@Schema(description = "Sprint Completed Response")
public class SprintCompletedResponse {
    @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Status", example = "ACTIVE")
    private String status;
    @Schema(description = "Velocity", example = "1")
    private Integer velocity;
    @Schema(description = "Completed at", example = "2023-12-31T23:59:59Z")
    private Instant completedAt;
    @Schema(description = "Report", example = "example")
    private SprintReport report;

    @Data
    @Builder
@Schema(description = "Sprint Report")
public static class SprintReport {
        @Schema(description = "Total tasks", example = "10")
        private long totalTasks;
        @Schema(description = "Done tasks", example = "1")
        private long doneTasks;
        @Schema(description = "Cancelled tasks", example = "1")
        private long cancelledTasks;
        @Schema(description = "Moved to backlog", example = "1")
        private long movedToBacklog;
        @Schema(description = "Velocity", example = "1")
        private Integer velocity;
        @Schema(description = "Completion rate", example = "10.5")
        private double completionRate;
    }
}
