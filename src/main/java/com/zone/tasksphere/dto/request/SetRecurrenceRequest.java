package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.RecurringFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "Set Recurrence Request")
public class SetRecurrenceRequest {

    @NotNull
    @Schema(description = "Frequency", example = "example")
    private RecurringFrequency frequency;

    /** WEEKLY: list of day-of-week values (1=Monday, 7=Sunday) */
    @Schema(description = "Days of week", example = "[]")
    private List<Integer> daysOfWeek;

    /** MONTHLY: day of month (1–31) */
    @Schema(description = "Day of month", example = "1")
    private Integer dayOfMonth;

    /** CUSTOM: Spring/Quartz cron expression */
    @Schema(description = "Cron expression", example = "string")
    private String cronExpression;

    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private LocalDate endDate;

    @Max(100)
    @Schema(description = "Max occurrences", example = "1")
    private Integer maxOccurrences;

    @Schema(description = "First run at (chỉ dùng khi tạo mới, bỏ qua khi cập nhật)", example = "2023-12-31T23:59:59Z")
    private LocalDateTime firstRunAt;
}
