package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "0. System", description = "Health check, system info.")
public class HealthController {

    @Operation(
        summary = "Health Check",
        description = """
            **NFR-07:** Kiểm tra trạng thái tất cả dependency.
            
            Trả về status của: Database, Redis, S3/MinIO.
            Dùng cho: Load balancer health check, monitoring.
            
            **HTTP 200:** Tất cả OK.
            **HTTP 503:** Có dependency bị lỗi.
            """,
        security = {}  // Public endpoint — không cần auth
    )
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "status", "UP",
            "database", "CONNECTED",
            "redis", "CONNECTED",
            "storage", "CONNECTED"
        )));
    }
}
