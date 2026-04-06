package com.zone.tasksphere.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnalyzeDescriptionRequest {

    @NotBlank(message = "description is required")
    @Size(max = 2000, message = "description must not exceed 2000 characters")
    private String description;
}
