package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.ExportFormat;
import com.zone.tasksphere.entity.enums.ExportJobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Export Job Status Response")
public class ExportJobStatusResponse {
    @Schema(description = "Job id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID jobId;
    @Schema(description = "Status", example = "ACTIVE")
    private ExportJobStatus status;
    @Schema(description = "Row count", example = "10")
    private Integer rowCount;
    @Schema(description = "Format", example = "2023-12-31T23:59:59Z")
    private ExportFormat format;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
    @Schema(description = "Expires at", example = "2023-12-31T23:59:59Z")
    private Instant expiresAt;
    @Schema(description = "Download url", example = "https://example.com/image.png")
    private String downloadUrl;  // only when DONE
    @Schema(description = "Error message", example = "string")
    private String errorMessage;
}
