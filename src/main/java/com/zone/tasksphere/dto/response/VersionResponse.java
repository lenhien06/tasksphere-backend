package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.VersionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Phản hồi thông tin version */
@Data
@Builder
@Schema(description = "Version Response")
public class VersionResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Description", example = "Description of the item")
    private String description;
    @Schema(description = "Status", example = "ACTIVE")
    private VersionStatus status;
    @Schema(description = "Release date", example = "2023-12-31T23:59:59Z")
    private LocalDate releaseDate;
    @Schema(description = "Task count", example = "10")
    private long taskCount;
    @Schema(description = "Done count", example = "10")
    private long doneCount;
    @Schema(description = "Completion rate", example = "10.5")
    private double completionRate;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
}
