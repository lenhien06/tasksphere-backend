package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.RecurrenceStatus;
import com.zone.tasksphere.entity.enums.RecurringFrequency;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Recurrence Response")
public class RecurrenceResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Task id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID taskId;
    @Schema(description = "Frequency", example = "example")
    private RecurringFrequency frequency;
    @Schema(description = "Frequency config", example = "example")
    private Map<String, Object> frequencyConfig;
    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private LocalDate endDate;
    @Schema(description = "Max occurrences", example = "1")
    private Integer maxOccurrences;
    @Schema(description = "Occurrence count", example = "10")
    private int occurrenceCount;
    @Schema(description = "Next run at", example = "2023-12-31T23:59:59Z")
    private LocalDateTime nextRunAt;
    @Schema(description = "Status", example = "ACTIVE")
    private RecurrenceStatus status;
    @Schema(description = "Remaining occurrences", example = "1")
    private int remainingOccurrences;
}
