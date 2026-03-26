package com.zone.tasksphere.dto.response;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Timeline / Gantt response")
public class TimelineViewResponse {

    @Schema(description = "Project id")
    private UUID projectId;

    @Schema(description = "Total tasks in timeline payload")
    private int totalTasks;

    @Schema(description = "Total blocker dependencies in timeline payload")
    private int totalDependencies;

    @Schema(description = "Tasks for timeline")
    private List<TimelineTaskItem> tasks;

    @Schema(description = "Canonical dependency edges for Gantt rendering")
    private List<TimelineDependencyEdge> dependencies;

    @Data
    @Builder
    public static class TimelineTaskItem {
        private UUID id;
        private String taskCode;
        private String title;
        private TaskStatus status;
        private TaskPriority priority;
        private UserSummary assignee;
        private LocalDate startDate;
        private LocalDate dueDate;
        private UUID parentTaskId;
        private List<DependencyRef> blockedBy;
        private List<DependencyRef> blocking;
    }

    @Data
    @Builder
    public static class TimelineDependencyEdge {
        private UUID linkId;
        private String linkType;
        private UUID blockerTaskId;
        private String blockerTaskCode;
        private String blockerTitle;
        private UUID blockedTaskId;
        private String blockedTaskCode;
        private String blockedTaskTitle;
    }

    @Data
    @Builder
    public static class DependencyRef {
        private UUID linkId;
        private UUID taskId;
        private String taskCode;
        private String title;
        private String linkType;
    }

    @Data
    @Builder
    public static class UserSummary {
        private UUID id;
        private String fullName;
        private String avatarUrl;
    }
}
