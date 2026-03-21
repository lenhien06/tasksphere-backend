package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Worklog Summary")
public class WorklogSummary {

    @Schema(description = "Total seconds", example = "10")
    private int totalSeconds;
    @Schema(description = "Total formatted", example = "10")
    private String totalFormatted;
    @Schema(description = "Logs", example = "[]")
    private List<WorklogResponse> logs;
}
