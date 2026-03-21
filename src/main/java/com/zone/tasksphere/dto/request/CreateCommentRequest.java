package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Create Comment Request")
public class CreateCommentRequest {

    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(max = 5000, message = "Nội dung không được vượt quá 5000 ký tự")
    @Schema(description = "Content", example = "string")
    private String content;

    @Schema(description = "Parent id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID parentId;
}
