package com.zone.tasksphere.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class GeneratedTaskDto {

    /** ≤80 chars, starts with action verb */
    private String title;

    /** Detailed description including acceptance criteria prose */
    private String description;

    /** task | story | bug */
    private String type;

    /** critical | high | medium | low */
    private String priority;

    @JsonProperty("story_points")
    private Integer storyPoints;

    @JsonProperty("skill_tags_required")
    private List<String> skillTagsRequired;

    @JsonProperty("acceptance_criteria")
    private String acceptanceCriteria;
}
