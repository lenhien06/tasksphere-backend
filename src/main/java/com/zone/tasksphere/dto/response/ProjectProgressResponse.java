package com.zone.tasksphere.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * FR-27: Project progress report response.
 */
@Data
@Builder
public class ProjectProgressResponse {

    private long totalTasks;
    private long doneTasks;
    private long inProgressTasks;
    private long todoTasks;
    private long overdueTasks;
    private double completionPercent;

    /** task count per status */
    private Map<String, Long> tasksByStatus;

    /** [{date, remainingPoints}] for burndown chart */
    private List<BurndownPoint> burndownData;

    @Data
    @Builder
    public static class BurndownPoint {
        private String date;           // yyyy-MM-dd
        private double remainingPoints;
        private double idealPoints;
    }
}
