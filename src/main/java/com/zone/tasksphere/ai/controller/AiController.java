package com.zone.tasksphere.ai.controller;

import com.zone.tasksphere.ai.dto.*;
import com.zone.tasksphere.ai.service.AiService;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.security.CustomUserDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI Module — 4 endpoints, all restricted to PROJECT_MANAGER.
 * Base: /api/v1/projects/{projectId}/ai
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    // ── Feature 1 ─────────────────────────────────────────────────────────────

    @PostMapping("/generate-tasks")
    @PreAuthorize("@projectPermissions.isProjectManager(#projectId)")
    public ResponseEntity<Map<String, Object>> generateTasks(
            @PathVariable String projectId,
            @Valid @RequestBody GenerateTasksRequest request) {

        List<GeneratedTaskDto> tasks = aiService.generateTasks(uuid(projectId), request);
        return ok(tasks);
    }

    @PostMapping("/confirm-tasks")
    @PreAuthorize("@projectPermissions.isProjectManager(#projectId)")
    public ResponseEntity<Map<String, Object>> confirmTasks(
            @PathVariable String projectId,
            @Valid @RequestBody ConfirmTasksRequest request,
            Authentication auth) {

        User reporter = currentUser(auth);
        ConfirmTasksResponse result = aiService.confirmTasks(uuid(projectId), request, reporter);
        return ok(result);
    }

    // ── Feature 2 ─────────────────────────────────────────────────────────────

    @PostMapping("/suggest-assignments")
    @PreAuthorize("@projectPermissions.isProjectManager(#projectId)")
    public ResponseEntity<Map<String, Object>> suggestAssignments(
            @PathVariable String projectId,
            @RequestBody(required = false) SuggestAssignmentsRequest request) {

        SuggestAssignmentsResponse result = aiService.suggestAssignments(uuid(projectId), request);
        return ok(result);
    }

    @PostMapping("/confirm-assignments")
    @PreAuthorize("@projectPermissions.isProjectManager(#projectId)")
    public ResponseEntity<Map<String, Object>> confirmAssignments(
            @PathVariable String projectId,
            @Valid @RequestBody ConfirmAssignmentsRequest request,
            Authentication auth) {

        UUID confirmerId = currentUser(auth).getId();
        AssignmentConfirmResult result =
                aiService.confirmAssignments(uuid(projectId), request, confirmerId);
        return ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("data", data, "meta", Map.of()));
    }

    private static UUID uuid(String id) {
        try { return UUID.fromString(id); }
        catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid project ID");
        }
    }

    private static User currentUser(Authentication auth) {
        CustomUserDetail detail = (CustomUserDetail) auth.getPrincipal();
        User u = new User();
        u.setId(detail.getUserDetail().getId());
        u.setFullName(detail.getUserDetail().getFullName());
        return u;
    }
}
