package com.zone.tasksphere.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfirmProjectPlanResponse {

    private String projectId;
    private String projectKey;
    private int sprintCount;
    private int taskCount;
    private int assignedCount;
    /** Frontend redirect URL */
    private String projectUrl;
}
