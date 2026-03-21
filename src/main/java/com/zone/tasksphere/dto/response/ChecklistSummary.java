package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Checklist Summary")
public class ChecklistSummary {

    @Schema(description = "Total", example = "10")
    private int total;
    @Schema(description = "Completed", example = "1")
    private int completed;
    @Schema(description = "Items", example = "[]")
    private List<ChecklistItemResponse> items;
}
