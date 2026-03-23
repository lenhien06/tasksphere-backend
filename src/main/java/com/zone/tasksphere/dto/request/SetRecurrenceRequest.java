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
@Schema(description = """
    Set Recurrence Request.
    **endDate** và **maxOccurrences**: phải có ít nhất một giá trị (không được cùng null) — HTTP 400 RECURRING_NO_END_CONDITION.
    Khi chỉ gửi endDate, BE lưu maxOccurrences nội bộ cao để job không dừng nhầm trước ngày kết thúc.
    """)
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

    @Schema(description = "Ngày kết thúc lặp (UTC date). Ít nhất một trong endDate | maxOccurrences.", example = "2026-12-31")
    private LocalDate endDate;

    @Max(100)
    @Schema(description = "Số lần generate tối đa (1–100 khi gửi lên). Ít nhất một trong endDate | maxOccurrences.", example = "10")
    private Integer maxOccurrences;

    @Schema(description = "First run at (chỉ dùng khi tạo mới, bỏ qua khi cập nhật)", example = "2023-12-31T23:59:59Z")
    private LocalDateTime firstRunAt;
}
