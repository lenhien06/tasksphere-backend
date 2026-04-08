package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Velocity forecast response")
public class VelocityForecastResponse {

    @Schema(description = "Chart categories")
    private List<String> categories;

    @Schema(description = "Committed points series")
    private List<Integer> committedSeries;

    @Schema(description = "Completed points series")
    private List<Integer> completedSeries;

    @Schema(description = "Average completed velocity")
    private double averageCompleted;

    @Schema(description = "Trend: UP | DOWN | STABLE")
    private String trend;

    @Schema(description = "Detailed sprint items")
    private List<SprintVelocityPoint> sprints;

    @Data
    @Builder
    @Schema(description = "Sprint velocity point")
    public static class SprintVelocityPoint {
        @Schema(description = "Sprint id")
        private UUID sprintId;

        @Schema(description = "Sprint name")
        private String sprintName;

        @Schema(description = "Sprint completed date")
        private LocalDate completedAt;

        @Schema(description = "Committed points recorded at sprint start")
        private int committedPoints;

        @Schema(description = "Completed points recorded at sprint completion")
        private int completedPoints;
    }
}
