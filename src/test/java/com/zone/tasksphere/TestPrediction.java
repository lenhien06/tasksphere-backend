package com.zone.tasksphere;

import com.zone.tasksphere.dto.AIPredictionRequest;
import com.zone.tasksphere.dto.AIPredictionResponse;
import org.springframework.web.client.RestTemplate;

public class TestPrediction {
    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();
        AIPredictionRequest request = AIPredictionRequest.builder()
                .employeeId(1)
                .hoursWorked(40.5)
                .tasksCompleted(10)
                .lateCount(1)
                .absenceCount(0)
                .build();
        try {
            AIPredictionResponse response = restTemplate.postForObject(
                    "http://127.0.0.1:8000/api/predict", request, AIPredictionResponse.class);
            System.out.println("Response: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
