package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Shift task schedule request for Timeline / Gantt")
public class ShiftTaskScheduleRequest {

    @NotNull
    @Schema(description = "Number of days to shift the task schedule", example = "3")
    private Integer shiftDays;

    @Schema(description = "Whether dependent tasks should also be shifted", example = "true")
    private Boolean autoShiftDependents = Boolean.FALSE;
}
