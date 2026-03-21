package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Member Search Response")
public class MemberSearchResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Full name", example = "John Doe")
    private String fullName;
    @Schema(description = "Email", example = "user@example.com")
    private String email;
    @Schema(description = "Avatar url", example = "https://example.com/image.png")
    private String avatarUrl;
    @Schema(description = "Project role", example = "example")
    private ProjectRole projectRole;
}
