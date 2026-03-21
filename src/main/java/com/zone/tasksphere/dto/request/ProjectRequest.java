package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Project Request")
public class ProjectRequest {
    @NotBlank(message = "Tên dự án không được để trống")
    @Size(max = 255, message = "Tên dự án không được vượt quá 255 ký tự")
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @NotBlank(message = "Key dự án không được để trống")
    @Pattern(regexp = "^[A-Z0-9]{2,10}$", message = "Key dự án phải từ 2 đến 10 ký tự, chỉ chứa chữ cái in hoa và số, không có khoảng trắng")
    @Schema(description = "Project key", example = "PROJ-123")
    private String projectKey;

    @Schema(description = "Description", example = "Description of the item")
    private String description;

    private ProjectVisibility visibility = ProjectVisibility.PRIVATE;

    @Schema(description = "Start date", example = "2023-12-31T23:59:59Z")
    private Instant startDate;

    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private Instant endDate;
}
