package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateSavedFilterRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.SavedFilterResponse;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.SavedFilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "18. Saved Filters", description = "Quản lý bộ lọc task đã lưu (P3-BE-09)")
@SecurityRequirement(name = "bearerAuth")
public class SavedFilterController {

    private final SavedFilterService savedFilterService;

    // ════════════════════════════════════════
    // POST /api/v1/projects/{projectId}/saved-filters
    // ════════════════════════════════════════
    @Operation(summary = "Lưu bộ lọc task",
               description = "Mọi member. Tối đa 10 bộ lọc/project/user.")
    @PostMapping("/api/v1/projects/{projectId}/saved-filters")
    public ResponseEntity<ApiResponse<SavedFilterResponse>> createFilter(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSavedFilterRequest request
    ) {
        SavedFilterResponse response = savedFilterService.createFilter(projectId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Bộ lọc đã được lưu"));
    }

    // ════════════════════════════════════════
    // GET /api/v1/projects/{projectId}/saved-filters
    // ════════════════════════════════════════
    @Operation(summary = "Lấy danh sách bộ lọc đã lưu",
               description = "Chỉ trả về bộ lọc của người dùng hiện tại.")
    @GetMapping("/api/v1/projects/{projectId}/saved-filters")
    public ResponseEntity<ApiResponse<List<SavedFilterResponse>>> getFilters(
            @PathVariable UUID projectId
    ) {
        List<SavedFilterResponse> response = savedFilterService.getFilters(projectId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ════════════════════════════════════════
    // DELETE /api/v1/saved-filters/{filterId}
    // ════════════════════════════════════════
    @Operation(summary = "Xóa bộ lọc đã lưu", description = "Owner hoặc PM.")
    @DeleteMapping("/api/v1/saved-filters/{filterId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteFilter(
            @PathVariable UUID filterId
    ) {
        savedFilterService.deleteFilter(filterId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Đã xóa bộ lọc")));
    }

    // ── Helper ───────────────────────────────────────────────────────

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new CustomAuthenticationException("Chưa đăng nhập hoặc phiên làm việc hết hạn");
        }
        CustomUserDetail userDetail = (CustomUserDetail) auth.getPrincipal();
        return userDetail.getUserDetail().getId();
    }
}
