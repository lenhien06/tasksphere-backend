package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Worklog Response")
public class WorklogResponse {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "User", example = "example")
    private UserSummary user;
    @Schema(description = "Time spent", example = "2023-12-31T23:59:59Z")
    private int timeSpent;
    @Schema(description = "Time spent formatted", example = "2023-12-31T23:59:59Z")
    private String timeSpentFormatted;
    @Schema(description = "Log date", example = "2023-12-31T23:59:59Z")
    private LocalDate logDate;
    @Schema(description = "Note", example = "string")
    private String note;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;

    @Data
    @Builder
@Schema(description = "User Summary")
public static class UserSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Full name", example = "John Doe")
        private String fullName;
        @Schema(description = "Avatar url", example = "https://example.com/image.png")
        private String avatarUrl;
    }
}
