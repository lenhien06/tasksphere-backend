package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.SprintStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Phản hồi tóm tắt dùng trong danh sách sprint */
@Data
@Builder
@Schema(description = "Sprint Summary Response")
public class SprintSummaryResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Goal", example = "string")
    private String goal;
    @Schema(description = "Status", example = "ACTIVE")
    private SprintStatus status;
    @Schema(description = "Start date", example = "2023-12-31T23:59:59Z")
    private LocalDate startDate;
    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private LocalDate endDate;
    @Schema(description = "Started at", example = "2023-12-31T23:59:59Z")
    private Instant startedAt;
    @Schema(description = "Completed at", example = "2023-12-31T23:59:59Z")
    private Instant completedAt;
    @Schema(description = "Velocity", example = "1")
    private Integer velocity;
    @Schema(description = "Task count", example = "10")
    private long taskCount;
    @Schema(description = "Done count", example = "10")
    private long doneCount;
    @Schema(description = "Completion rate", example = "10.5")
    private double completionRate;
}
