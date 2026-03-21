package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update Checklist Item Request")
public class UpdateChecklistItemRequest {

    @Size(max = 500, message = "Tiêu đề không được vượt quá 500 ký tự")
    @Schema(description = "Title", example = "Item Title")
    private String title;

    @Schema(description = "Is done", example = "true")
    private Boolean isDone;
}
