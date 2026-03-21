package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Update Worklog Request")
public class UpdateWorklogRequest {

    @NotNull(message = "Thời gian làm việc không được null")
    @Min(value = 1, message = "WRK_001: Thời gian tối thiểu là 1 giây")
    @Max(value = 86400, message = "WRK_001: Thời gian tối đa là 86400 giây (24 giờ)")
    @Schema(description = "Time spent", example = "2023-12-31T23:59:59Z")
    private Integer timeSpent;

    @NotNull(message = "Ngày log không được null")
    @PastOrPresent(message = "WRK_002: Ngày log không được là ngày tương lai")
    @Schema(description = "Log date", example = "2023-12-31T23:59:59Z")
    private LocalDate logDate;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    @Schema(description = "Note", example = "string")
    private String note;
}
