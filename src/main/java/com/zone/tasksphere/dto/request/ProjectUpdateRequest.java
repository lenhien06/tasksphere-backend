package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ProjectVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Project Update Request")
public class ProjectUpdateRequest {
    @NotBlank(message = "Tên dự án không được để trống")
    @Size(max = 255, message = "Tên dự án không được vượt quá 255 ký tự")
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @Schema(description = "Description", example = "Description of the item")
    private String description;

    @NotNull(message = "Quyền hiển thị không được để trống")
    private ProjectVisibility visibility = ProjectVisibility.PRIVATE;

    @Schema(description = "Start date", example = "2023-12-31T23:59:59Z")
    private Instant startDate;

    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private Instant endDate;
}
