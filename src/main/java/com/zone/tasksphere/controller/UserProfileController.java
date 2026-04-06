package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.UpdateUserProfileRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.UserProfileResponse;
import com.zone.tasksphere.service.UserProfileService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "2. User Management")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "Lấy profile đầy đủ (kèm projects đã tham gia)")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        UUID userId = AuthUtils.getUserDetail().getId();
        return ResponseEntity.ok(ApiResponse.success(userProfileService.getProfile(userId)));
    }

    @Operation(summary = "Cập nhật thông tin profile (fullName, jobTitle, bio, workCapacityHours)")
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateUserProfileRequest request) {
        UUID userId = AuthUtils.getUserDetail().getId();
        return ResponseEntity.ok(ApiResponse.success(userProfileService.updateProfile(userId, request)));
    }
}
