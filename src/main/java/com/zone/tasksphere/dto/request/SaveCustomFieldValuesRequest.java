package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Save Custom Field Values Request")
public class SaveCustomFieldValuesRequest {

    @NotNull
    @NotEmpty
    @Schema(description = "Values", example = "[]")
    private List<CustomFieldValueItem> values;

    @Data
@Schema(description = "Custom Field Value Item")
public static class CustomFieldValueItem {
        @NotNull(message = "fieldId là bắt buộc cho mỗi phần tử values")
        @Schema(description = "Field id", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID fieldId;
        @Schema(description = "Value", example = "string")
        private String value; // null = delete
    }
}
