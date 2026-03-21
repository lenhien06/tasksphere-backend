package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.ExportJobStatusResponse;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

public interface ExportService {

    ResponseEntity<?> createExportJob(UUID projectId, String format, String scope,
                                       String sprintId, UUID currentUserId);

    ExportJobStatusResponse getJobStatus(UUID jobId, UUID currentUserId);

    ResponseEntity<?> getDownloadResponse(UUID jobId, UUID currentUserId);
}
