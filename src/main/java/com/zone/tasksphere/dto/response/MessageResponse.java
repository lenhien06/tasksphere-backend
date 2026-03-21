package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Message Response")
public class MessageResponse {
    @Schema(description = "Message", example = "string")
    String message;
}

