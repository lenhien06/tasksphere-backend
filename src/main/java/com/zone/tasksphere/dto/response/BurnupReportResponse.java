package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Burnup report response")
public class BurnupReportResponse {

    @Schema(description = "Sprint id")
    private UUID sprintId;

    @Schema(description = "Sprint name")
    private String sprintName;

    @Schema(description = "Sprint start date")
    private LocalDate startDate;

    @Schema(description = "Sprint end date")
    private LocalDate endDate;

    @Schema(description = "Scope at sprint start")
    private int initialScopePoints;

    @Schema(description = "Latest scope in the selected period")
    private int latestScopePoints;

    @Schema(description = "Latest completed points in the selected period")
    private int latestCompletedPoints;

    @Schema(description = "Daily burnup points")
    private List<DataPoint> data;

    @Data
    @Builder
    @Schema(description = "Burnup data point")
    public static class DataPoint {
        @Schema(description = "Date in sprint")
        private LocalDate date;

        @Schema(description = "Total scope points at end of day")
        private int scopePoints;

        @Schema(description = "Completed points at end of day")
        private int completedPoints;
    }
}
