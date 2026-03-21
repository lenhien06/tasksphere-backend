package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phản hồi báo cáo velocity của project */
@Data
@Builder
@Schema(description = "Velocity Report Response")
public class VelocityReportResponse {
    @Schema(description = "Sprints", example = "[]")
    private List<SprintVelocity> sprints;
    @Schema(description = "Average velocity", example = "10.5")
    private double averageVelocity;
    /** UP | DOWN | STABLE */
    @Schema(description = "Trend", example = "string")
    private String trend;

    @Data
    @Builder
@Schema(description = "Sprint Velocity")
public static class SprintVelocity {
        @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID sprintId;
        @Schema(description = "Sprint name", example = "John Doe")
        private String sprintName;
        @Schema(description = "Completed at", example = "2023-12-31T23:59:59Z")
        private LocalDate completedAt;
        @Schema(description = "Velocity", example = "1")
        private Integer velocity;
        @Schema(description = "Total tasks", example = "10")
        private long totalTasks;
        @Schema(description = "Done tasks", example = "1")
        private long doneTasks;
    }
}
