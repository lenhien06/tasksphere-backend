package com.zone.tasksphere.ai.controller;

import com.zone.tasksphere.ai.dto.*;
import com.zone.tasksphere.ai.service.WorkspaceAiService;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.utils.AuthUtils;
import com.zone.tasksphere.dto.response.UserDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Workspace-level AI endpoints — conversational project creation flow.
 * Base: /api/v1/workspaces/{wsId}/ai
 */
@RestController
@RequestMapping("/api/v1/workspaces/{wsId}/ai")
@RequiredArgsConstructor
@Tag(name = "11. Workspace AI", description = "AI tạo dự án conversational — phân tích → hỏi → sinh plan → confirm.")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceAiController {

    private final WorkspaceAiService workspaceAiService;

    @Operation(
        summary = "Bước 1 — Phân tích mô tả, trả về câu hỏi còn thiếu",
        description = """
            PM nhập 1-2 câu mô tả → AI trích xuất những gì đã hiểu và trả về
            tối đa 3 câu hỏi (techStack, deadline, teamMembers) kèm options có thể click.
            """
    )
    @PostMapping("/analyze-description")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AnalyzeDescriptionResponse>> analyzeDescription(
            @PathVariable UUID wsId,
            @Valid @RequestBody AnalyzeDescriptionRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        AnalyzeDescriptionResponse response =
                workspaceAiService.analyzeDescription(wsId, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Bước 2 — Sinh project plan hoàn chỉnh",
        description = """
            Khi đã thu thập đủ techStack + deadline + memberIds, gọi endpoint này
            để AI sinh toàn bộ project + sprint + task list dạng JSON.
            PM review rồi mới confirm tạo thật.
            """
    )
    @PostMapping("/generate-project-plan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<GenerateProjectPlanResponse>> generateProjectPlan(
            @PathVariable UUID wsId,
            @Valid @RequestBody GenerateProjectPlanRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        GenerateProjectPlanResponse response =
                workspaceAiService.generateProjectPlan(wsId, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Bước 3 — Confirm plan, tạo tất cả vào DB trong 1 transaction",
        description = """
            PM xác nhận plan → backend tạo project + sprints + tasks + members nguyên tử.
            Trả về projectId và URL để redirect.
            """
    )
    @PostMapping("/confirm-project-plan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ConfirmProjectPlanResponse>> confirmProjectPlan(
            @PathVariable UUID wsId,
            @Valid @RequestBody ConfirmProjectPlanRequest request) {

        UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        ConfirmProjectPlanResponse response =
                workspaceAiService.confirmProjectPlan(wsId, request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Dự án đã được tạo thành công!"));
    }
}
