package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.AIPredictionResponse;
import java.util.UUID;

public interface AIPredictionService {
    AIPredictionResponse getPerformancePrediction(UUID userId);
}
