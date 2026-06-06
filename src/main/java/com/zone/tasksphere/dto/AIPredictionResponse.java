package com.zone.tasksphere.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIPredictionResponse {
    @JsonProperty("employee_id")
    private String employeeId;
    
    @JsonProperty("predicted_performance_score")
    private Double predictedPerformanceScore;
    
    private String trend;
    
    private String errorMessage;
}
