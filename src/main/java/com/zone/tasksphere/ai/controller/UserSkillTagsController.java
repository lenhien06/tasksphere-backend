package com.zone.tasksphere.ai.controller;

import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.security.CustomUserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PATCH /api/v1/users/me/skill-tags
 * Allows authenticated users to manage their own skill tags.
 * skill_tags are used by ScoringEngine (Feature 2) when AI suggests assignments.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserSkillTagsController {

    private final UserRepository userRepository;

    @PatchMapping("/skill-tags")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> updateSkillTags(
            @RequestBody Map<String, List<String>> body,
            Authentication auth) {

        List<String> rawTags = body.get("skillTags");
        if (rawTags == null) {
            return error("skillTags field is required");
        }
        if (rawTags.size() > 20) {
            return error("Tối đa 20 skill tags được phép");
        }

        List<String> sanitized;
        try {
            sanitized = rawTags.stream()
                    .map(String::trim)
                    .filter(t -> !t.isBlank())
                    .distinct()
                    .peek(tag -> {
                        if (tag.length() > 50)
                            throw new IllegalArgumentException("Tag '" + tag + "' vượt quá 50 ký tự");
                        if (!tag.matches("[\\w\\s.+#/\\-]{1,50}"))
                            throw new IllegalArgumentException("Tag '" + tag + "' chứa ký tự không hợp lệ");
                    })
                    .toList();
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }

        UUID userId = ((CustomUserDetail) auth.getPrincipal()).getUserDetail().getId();
        User user   = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        user.setSkillTags(sanitized);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "data", Map.of("userId", userId.toString(), "skillTags", sanitized),
                "meta", Map.of()));
    }

    private static ResponseEntity<Map<String, Object>> error(String detail) {
        return ResponseEntity.badRequest().body(Map.of(
                "type",   "https://tasksphere.dev/problems/validation-error",
                "title",  "Validation Error",
                "status", 400,
                "detail", detail));
    }
}
