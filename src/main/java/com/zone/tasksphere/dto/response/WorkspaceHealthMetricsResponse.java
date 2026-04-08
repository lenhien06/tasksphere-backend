package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Workspace health dashboard metrics")
public class WorkspaceHealthMetricsResponse {

    private UUID workspaceId;
    private String workspaceName;
    private double globalProgress;
    private int overdueTaskCount;
    private int riskyProjectCount;
    private int totalTaskCount;
    private int doneTaskCount;
    private TaskDistribution taskDistribution;
    private SprintHealth sprintHealth;
    private ProjectHighlight focusProject;
    private List<BurndownPoint> burndown;
    private List<RiskHotspot> hotspots;
    private List<ResourceAlert> overloadedMembers;
    private List<ProjectHealthItem> projects;
    private List<MemberPreview> memberPreview;
    private Instant generatedAt;
    private Instant cachedUntil;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDistribution {
        private int todo;
        private int inProgress;
        private int done;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SprintHealth {
        private String sprintName;
        private Integer daysRemaining;
        private int totalStoryPoints;
        private int overdueTasks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectHighlight {
        private UUID projectId;
        private String projectName;
        private String projectKey;
        private String riskLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BurndownPoint {
        private String label;
        private int idealRemaining;
        private int actualRemaining;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskHotspot {
        private UUID taskId;
        private String taskCode;
        private String title;
        private LocalDate dueDate;
        private String priority;
        private boolean overdue;
        private UUID projectId;
        private String projectName;
        private UUID assigneeId;
        private String assigneeName;
        private String assigneeAvatarUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceAlert {
        private UUID userId;
        private String fullName;
        private String avatarUrl;
        private int allocatedHours;
        private int capacityHours;
        private boolean overloaded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectHealthItem {
        private UUID projectId;
        private String projectName;
        private String projectKey;
        private String status;
        private String riskLevel;
        private String activeSprintName;
        private Integer daysRemaining;
        private int totalTasks;
        private int doneTasks;
        private int overdueTasks;
        private int totalStoryPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberPreview {
        private UUID userId;
        private String fullName;
        private String avatarUrl;
        private String role;
    }
}
