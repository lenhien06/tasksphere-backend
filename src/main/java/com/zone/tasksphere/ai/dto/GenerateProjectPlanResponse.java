package com.zone.tasksphere.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Full project plan returned by generate-project-plan.
 * Designed to be (a) returned to the FE for review, and (b) passed back
 * verbatim in confirm-project-plan so the backend can persist everything
 * without re-calling the LLM.
 */
@Data
public class GenerateProjectPlanResponse {

    private ProjectPlanDto project;
    private List<SprintPlanDto> sprints;
    private int totalTasks;
    private int totalStoryPoints;

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    @Data
    public static class ProjectPlanDto {
        private String name;
        private String projectKey;
        private String description;
        private int estimatedWeeks;
    }

    @Data
    public static class SprintPlanDto {
        private String name;
        private String goal;
        private int weekStart;
        private int weekEnd;
        private List<TaskPlanDto> tasks;
    }

    @Data
    public static class TaskPlanDto {
        private String title;
        private String type;
        private String priority;
        private int storyPoints;
        @JsonProperty("skillTagsRequired")
        private List<String> skillTagsRequired;
        /** UUID of the suggested assignee (must be one of memberIds passed in). */
        private String suggestedAssigneeId;
    }
}
