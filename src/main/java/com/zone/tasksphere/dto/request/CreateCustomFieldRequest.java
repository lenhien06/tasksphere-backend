package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.CustomFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Create Custom Field Request")
public class CreateCustomFieldRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "Name", example = "John Doe")
    private String name;

    @NotNull
    @Schema(description = "Field type", example = "TASK")
    private CustomFieldType fieldType;

    @Schema(description = "Options", example = "[]")
    private List<String> options;

    private boolean required = false;

    private int position = 0;
}
