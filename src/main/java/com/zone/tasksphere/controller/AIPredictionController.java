package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.AIPredictionResponse;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.service.AIPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "AI Prediction API")
public class AIPredictionController {

    private final AIPredictionService aiPredictionService;

    @Operation(summary = "Get user performance prediction from AI")
    @GetMapping("/{userId}/performance-prediction")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AIPredictionResponse>> getPerformancePrediction(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(aiPredictionService.getPerformancePrediction(userId)));
    }
}
