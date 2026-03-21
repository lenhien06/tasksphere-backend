package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.CustomFieldType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(description = "Custom Field Value Response")
public class CustomFieldValueResponse {
    @Schema(description = "Field id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID fieldId;
    @Schema(description = "Field name", example = "John Doe")
    private String fieldName;
    @Schema(description = "Field type", example = "TASK")
    private CustomFieldType fieldType;
    @Schema(description = "Value", example = "string")
    private String value;
    @Schema(description = "Typed value", example = "TASK")
    private Object typedValue;
}
