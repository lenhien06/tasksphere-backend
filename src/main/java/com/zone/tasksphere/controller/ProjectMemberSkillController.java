package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.MemberSkillResponse;
import com.zone.tasksphere.service.ProjectMemberSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
@RequiredArgsConstructor
@Tag(name = "5. Project Members")
@SecurityRequirement(name = "bearerAuth")
public class ProjectMemberSkillController {

    private final ProjectMemberSkillService projectMemberSkillService;

    @Operation(summary = "Lấy danh sách member kèm skill tags (dùng cho AI skill modal)")
    @GetMapping("/skills")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<MemberSkillResponse>>> getMemberSkills(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(
                ApiResponse.success(projectMemberSkillService.getMemberSkills(projectId)));
    }

    @Operation(summary = "[PM] Cập nhật skill tags của một member")
    @PatchMapping("/{userId}/skills")
    @PreAuthorize("@projectPermissions.isProjectManager(#projectId)")
    public ResponseEntity<ApiResponse<MemberSkillResponse>> updateMemberSkills(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberSkillsRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(projectMemberSkillService.updateMemberSkills(projectId, userId, request)));
    }
}
