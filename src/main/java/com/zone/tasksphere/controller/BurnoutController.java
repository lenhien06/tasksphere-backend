package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.BurnoutAiRequest;
import com.zone.tasksphere.dto.request.BurnoutAnalyzeRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.BurnoutAiResponse;
import com.zone.tasksphere.dto.response.BurnoutAnalyzeResponse;
import com.zone.tasksphere.dto.response.BurnoutDataPoint;
import com.zone.tasksphere.service.BurnoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/burnout")
@RequiredArgsConstructor
@Tag(name = "Burnout Detector", description = "Module 3 — Phát hiện Burnout bằng Sliding Window + AI")
public class BurnoutController {

    private final BurnoutService burnoutService;

    @Operation(summary = "Sinh dữ liệu giả lập 180 ngày cho một developer")
    @GetMapping("/demo-data")
    public ResponseEntity<ApiResponse<List<BurnoutDataPoint>>> getDemoData(
            @RequestParam(defaultValue = "Alex") String developerName) {
        List<BurnoutDataPoint> data = burnoutService.generateDemoData(developerName);
        return ResponseEntity.ok(ApiResponse.success(data, "Demo data generated successfully"));
    }

    @Operation(summary = "Chạy thuật toán Longest Increasing Subarray để tìm chuỗi burnout dài nhất")
    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<BurnoutAnalyzeResponse>> analyze(
            @RequestBody BurnoutAnalyzeRequest request) {
        BurnoutAnalyzeResponse result = burnoutService.analyze(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Analysis complete"));
    }

    @Operation(summary = "Gọi AI tạo tin nhắn Slack 1-on-1 theo phong cách Engineering Manager")
    @PostMapping("/ai-message")
    public ResponseEntity<ApiResponse<BurnoutAiResponse>> generateAiMessage(
            @RequestBody BurnoutAiRequest request) {
        BurnoutAiResponse response = burnoutService.generateAiMessage(request);
        return ResponseEntity.ok(ApiResponse.success(response, "AI message generated"));
    }
}
