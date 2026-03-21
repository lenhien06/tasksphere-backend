package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Create Sprint Request")
public class CreateSprintRequest {

    @NotBlank(message = "Tên sprint không được để trống")
    @Size(max = 100, message = "Tên sprint tối đa 100 ký tự")
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @Size(max = 500, message = "Mục tiêu sprint tối đa 500 ký tự")
    @Schema(description = "Goal", example = "string")
    private String goal;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @Schema(description = "Start date", example = "2023-12-31T23:59:59Z")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private LocalDate endDate;
}
