package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Timeline schedule shift result")
public class ShiftTaskScheduleResponse {

    @Schema(description = "Root task id that initiated the shift")
    private UUID taskId;

    @Schema(description = "Applied shift in days")
    private int shiftDays;

    @Schema(description = "Whether dependent tasks were shifted too")
    private boolean autoShiftDependents;

    @Schema(description = "Ids of tasks whose schedules were updated")
    private List<UUID> updatedTaskIds;

    @Schema(description = "How many tasks were updated")
    private int affectedTasks;
}
