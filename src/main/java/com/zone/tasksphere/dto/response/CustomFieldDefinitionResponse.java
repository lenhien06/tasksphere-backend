package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.CustomFieldType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Custom Field Definition Response")
public class CustomFieldDefinitionResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Field type", example = "TASK")
    private CustomFieldType fieldType;
    @Schema(description = "Options", example = "[]")
    private List<String> options;
    @Schema(description = "Required", example = "true")
    private boolean required;
    @Schema(description = "Hidden", example = "550e8400-e29b-41d4-a716-446655440000")
    private boolean hidden;
    @Schema(description = "Position", example = "1")
    private int position;
    @Schema(description = "Has values", example = "true")
    private boolean hasValues;
}
