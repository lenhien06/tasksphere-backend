package com.zone.tasksphere.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    @JsonProperty("health_score")
    private Integer healthScore;
    
    private String trend;

    @JsonProperty("history")
    private List<Double> history;

    @JsonProperty("attrition_probability")
    private Double attritionProbability;

    @JsonProperty("top_contributing_factors")
    private List<String> topContributingFactors;

    @JsonProperty("root_causes")
    private List<String> rootCauses;

    private List<String> recommendations;

    private Double confidence;
    
    private String errorMessage;
}
