package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Auth Response")
public class AuthResponse {

    @Schema(description = "Access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String accessToken;
    @Schema(description = "Refresh token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String refreshToken;
    @Schema(description = "Expires in", example = "1")
    int expiresIn;
    @Schema(description = "Token type", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String tokenType;

    // User info
    @Schema(description = "User id", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID userId;
    @Schema(description = "Full name", example = "John Doe")
    String fullName;
    @Schema(description = "Email", example = "user@example.com")
    String email;
    @Schema(description = "Avatar url", example = "https://example.com/image.png")
    String avatarUrl;
    @Schema(description = "Role", example = "string")
    String role;
    @Schema(description = "System role", example = "string")
    String systemRole;
}
