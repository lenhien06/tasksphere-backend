package com.zone.tasksphere.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * PM confirms the plan and sends it back for atomic DB creation.
 */
@Data
public class ConfirmProjectPlanRequest {

    @NotNull(message = "plan is required")
    private PlanPayload plan;

    @Data
    public static class PlanPayload {
        /** Final project metadata (PM may have edited inline) */
        @NotNull
        private GenerateProjectPlanResponse.ProjectPlanDto project;

        @NotNull
        private List<GenerateProjectPlanResponse.SprintPlanDto> sprints;

        /** Workspace member IDs to add to the project */
        private List<String> memberIds;
    }
}
