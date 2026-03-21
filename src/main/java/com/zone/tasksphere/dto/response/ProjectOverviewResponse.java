package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectOverviewResponse {

    private UUID projectId;
    private String projectName;
    private UUID sprintId;
    private String sprintName;

    private int totalTasks;
    private double completionRate;
    private int overdueTasks;
    private int totalStoryPoints;
    private int doneStoryPoints;

    private List<StatusDistributionItem> statusDistribution;
    private double overallProgress;

    private Instant generatedAt;
    private Instant cachedUntil;

    // ── Delta fields ─────────────────────────────────────────────────────
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Double completionRateDelta;   // null nếu project < 7 ngày

    private int backlogCount;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer backlogCountDelta;    // null nếu không đủ data

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer sprintDaysRemaining;  // null nếu không có sprint active

    private int memberCount;
    private int newMembersLast7Days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusDistributionItem {
        private String status;
        private int count;
        private double percentage;
    }
}
