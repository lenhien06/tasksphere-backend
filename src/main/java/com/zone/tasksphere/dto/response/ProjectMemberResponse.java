package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Project Member Response")
public class ProjectMemberResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "User", example = "example")
    private UserInfo user;
    @Schema(description = "Project role", example = "example")
    private ProjectRole projectRole;
    @Schema(description = "Joined at", example = "2023-12-31T23:59:59Z")
    private Instant joinedAt;

    @Data
    @Builder
@Schema(description = "User Info")
public static class UserInfo {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Full name", example = "John Doe")
        private String fullName;
        @Schema(description = "Email", example = "user@example.com")
        private String email;
        @Schema(description = "Avatar url", example = "https://example.com/image.png")
        private String avatarUrl;
    }
}