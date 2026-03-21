package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Update Custom Field Request")
public class UpdateCustomFieldRequest {

    @Size(max = 100)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @Schema(description = "Options", example = "[]")
    private List<String> options;

    @Schema(description = "Position", example = "1")
    private Integer position;

    @Schema(description = "Required", example = "true")
    private Boolean required;
}
