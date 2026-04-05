package com.zone.tasksphere.ai.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class GenerateTasksRequest {

    @NotBlank(message = "projectName is required")
    private String projectName;

    @NotBlank(message = "requirementsText is required")
    @Size(max = 5000, message = "requirementsText must not exceed 5000 characters")
    private String requirementsText;

    private String techStack;

    @Min(value = 1, message = "maxTasks must be ≥ 1")
    @Max(value = 30, message = "maxTasks must be ≤ 30")
    private int maxTasks = 10;
}
