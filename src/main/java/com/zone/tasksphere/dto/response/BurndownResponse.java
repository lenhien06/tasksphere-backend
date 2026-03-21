package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phản hồi biểu đồ burndown chart */
@Data
@Builder
@Schema(description = "Burndown Response")
public class BurndownResponse {
    @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID sprintId;
    @Schema(description = "Sprint name", example = "John Doe")
    private String sprintName;
    @Schema(description = "Start date", example = "2023-12-31T23:59:59Z")
    private LocalDate startDate;
    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private LocalDate endDate;
    @Schema(description = "Total story points", example = "10")
    private int totalStoryPoints;
    @Schema(description = "Ideal line", example = "550e8400-e29b-41d4-a716-446655440000")
    private List<DataPoint> idealLine;
    @Schema(description = "Actual line", example = "[]")
    private List<DataPoint> actualLine;

    @Data
    @Builder
@Schema(description = "Data Point")
public static class DataPoint {
        @Schema(description = "Date", example = "2023-12-31T23:59:59Z")
        private LocalDate date;
        @Schema(description = "Remaining points", example = "10.5")
        private double remainingPoints;
    }
}
