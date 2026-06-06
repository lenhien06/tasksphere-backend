package com.zone.tasksphere.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIPredictionRequest {
    @JsonProperty("employee_id")
    private int employeeId;
    @JsonProperty("hours_worked")
    private double hoursWorked;
    @JsonProperty("tasks_completed")
    private long tasksCompleted;
    @JsonProperty("late_count")
    private long lateCount;
}
