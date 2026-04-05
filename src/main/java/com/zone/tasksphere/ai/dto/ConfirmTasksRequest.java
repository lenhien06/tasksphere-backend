package com.zone.tasksphere.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ConfirmTasksRequest {

    /** Optional: assign created tasks to a sprint */
    private String sprintId;

    @NotEmpty(message = "tasks must not be empty")
    private List<GeneratedTaskDto> tasks;
}
