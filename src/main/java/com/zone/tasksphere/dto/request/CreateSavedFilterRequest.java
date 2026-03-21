package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "Create Saved Filter Request")
public class CreateSavedFilterRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @NotNull
    @Schema(description = "Filter criteria", example = "example")
    private Map<String, Object> filterCriteria;
}
