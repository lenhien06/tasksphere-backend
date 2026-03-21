package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.UploadJobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Upload Job Response")
public class UploadJobResponse {
    @Schema(description = "Job id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID jobId;
    @Schema(description = "Status", example = "ACTIVE")
    private UploadJobStatus status;
    @Schema(description = "File name", example = "John Doe")
    private String fileName;
    @Schema(description = "Message", example = "string")
    private String message;
    @Schema(description = "Attachment", example = "example")
    private AttachmentResponse attachment;  // only when DONE
    @Schema(description = "Error message", example = "string")
    private String errorMessage;
}
