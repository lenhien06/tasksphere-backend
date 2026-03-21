package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Create Version Request")
public class CreateVersionRequest {

    @NotBlank(message = "Tên version không được để trống")
    @Size(max = 100, message = "Tên version tối đa 100 ký tự")
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    @Schema(description = "Description", example = "Description of the item")
    private String description;

    @Schema(description = "Release date", example = "2023-12-31T23:59:59Z")
    private LocalDate releaseDate;
}
