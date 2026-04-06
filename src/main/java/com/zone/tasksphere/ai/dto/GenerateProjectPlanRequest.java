package com.zone.tasksphere.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GenerateProjectPlanRequest {

    @NotBlank(message = "description is required")
    @Size(max = 2000)
    private String description;

    /** "React,Node.js,MongoDB" or null → AI uses default */
    private String techStack;

    /** Number of weeks; null → default 4 weeks */
    private Integer deadlineWeeks;

    /** Workspace member UUIDs to include in the project */
    private List<String> memberIds;
}
