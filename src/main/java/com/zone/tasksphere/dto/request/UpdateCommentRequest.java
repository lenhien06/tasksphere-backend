package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update Comment Request")
public class UpdateCommentRequest {

    @NotBlank(message = "Nội dung bình luận không được để trống")
    @Size(max = 5000, message = "Nội dung không được vượt quá 5000 ký tự")
    @Schema(description = "Content", example = "string")
    private String content;
}
