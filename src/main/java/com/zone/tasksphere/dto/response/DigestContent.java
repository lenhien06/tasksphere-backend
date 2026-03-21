package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "Digest Content")
public class DigestContent {

    @Schema(description = "Overdue tasks", example = "[]")
    private List<TaskDigestItem> overdueTasks;
    @Schema(description = "Due today tasks", example = "[]")
    private List<TaskDigestItem> dueTodayTasks;
    @Schema(description = "Newly assigned tasks", example = "[]")
    private List<TaskDigestItem> newlyAssignedTasks;

    public boolean isEmpty() {
        return (overdueTasks == null || overdueTasks.isEmpty())
                && (dueTodayTasks == null || dueTodayTasks.isEmpty())
                && (newlyAssignedTasks == null || newlyAssignedTasks.isEmpty());
    }
}
