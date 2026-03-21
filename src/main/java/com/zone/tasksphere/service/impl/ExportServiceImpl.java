package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.ExportJobStatusResponse;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.ExportService;
import com.zone.tasksphere.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportServiceImpl implements ExportService {

    private static final int SYNC_MAX_ROWS = 1000;

    private final ExportJobRepository exportJobRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final SprintRepository sprintRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;
    private final MinioStorageService minioStorageService;
    private final WebSocketService webSocketService;

    @Override
    @Transactional
    public ResponseEntity<?> createExportJob(UUID projectId, String format, String scope,
                                              String sprintId, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new NotFoundException("Project not found"));

        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }

        User requestedBy = userRepository.findById(currentUserId)
            .orElseThrow(() -> new NotFoundException("User not found"));

        ExportFormat exportFormat;
        try {
            exportFormat = ExportFormat.valueOf(format.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Format không hợp lệ. Phải là EXCEL hoặc PDF");
        }

        ExportScope exportScope;
        try {
            exportScope = ExportScope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException e) {
            exportScope = ExportScope.ALL;
        }

        UUID sprintUuid = null;
        if (exportScope == ExportScope.SPRINT && sprintId != null) {
            sprintUuid = UUID.fromString(sprintId);
        }

        // Get tasks to export
        List<Task> tasks = getTasksForExport(projectId, exportScope, sprintUuid);
        int rowCount = tasks.size();

        if (rowCount <= SYNC_MAX_ROWS) {
            // Synchronous export
            return buildSyncExportResponse(tasks, project, exportFormat);
        } else {
            // Async export
            ExportJob job = ExportJob.builder()
                .project(project)
                .requestedBy(requestedBy)
                .format(exportFormat)
                .scope(exportScope)
                .sprintId(sprintUuid)
                .status(ExportJobStatus.PENDING)
                .build();
            job = exportJobRepository.save(job);

            processExportAsync(job, tasks);

            Map<String, Object> responseData = Map.of(
                "jobId", job.getId(),
                "status", "PENDING",
                "estimatedRows", rowCount,
                "message", "Export đang được xử lý. Polling GET /api/v1/export/jobs/" + job.getId() + "/status"
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(responseData));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ExportJobStatusResponse getJobStatus(UUID jobId, UUID currentUserId) {
        ExportJob job = exportJobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Export job not found: " + jobId));

        if (!job.getRequestedBy().getId().equals(currentUserId)) {
            throw new Forbidden("Bạn không có quyền xem job này");
        }

        if (job.getStatus() == ExportJobStatus.EXPIRED) {
            throw new BusinessRuleException("EXPORT_EXPIRED: File đã hết hạn (24h)");
        }

        return ExportJobStatusResponse.builder()
            .jobId(job.getId())
            .status(job.getStatus())
            .rowCount(job.getRowCount())
            .format(job.getFormat())
            .createdAt(job.getCreatedAt())
            .expiresAt(job.getExpiresAt())
            .downloadUrl(job.getStatus() == ExportJobStatus.DONE ? job.getDownloadUrl() : null)
            .errorMessage(job.getErrorMessage())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseEntity<?> getDownloadResponse(UUID jobId, UUID currentUserId) {
        ExportJob job = exportJobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Export job not found: " + jobId));

        if (!job.getRequestedBy().getId().equals(currentUserId)) {
            throw new Forbidden("Bạn không có quyền tải file này");
        }

        if (job.getStatus() == ExportJobStatus.EXPIRED
                || (job.getExpiresAt() != null && job.getExpiresAt().isBefore(Instant.now()))) {
            return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error("File đã hết hạn (24h). Vui lòng tạo export mới."));
        }

        if (job.getStatus() != ExportJobStatus.DONE) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("File chưa sẵn sàng. Status: " + job.getStatus()));
        }

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(job.getDownloadUrl()))
            .build();
    }

    // ── Async Processing ──────────────────────────────────────────────────────

    @Async("taskExecutor")
    @Transactional
    public void processExportAsync(ExportJob job, List<Task> tasks) {
        try {
            job.setStatus(ExportJobStatus.PROCESSING);
            exportJobRepository.save(job);

            byte[] fileBytes;
            String fileName;
            String contentType;
            String ext;

            if (job.getFormat() == ExportFormat.EXCEL) {
                fileBytes = excelExportService.exportTasksToExcel(tasks, job.getProject());
                ext = "xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else {
                fileBytes = pdfExportService.exportTasksToPdf(tasks, job.getProject());
                ext = "pdf";
                contentType = "application/pdf";
            }

            fileName = "tasks-" + job.getProject().getProjectKey() + "-" + LocalDate.now() + "." + ext;
            String storageKey = "exports/" + job.getId() + "/" + fileName;

            minioStorageService.uploadBytes(fileBytes, storageKey, contentType);

            String downloadUrl = minioStorageService.generateDownloadUrl(storageKey,
                Duration.ofHours(24));

            job.setStatus(ExportJobStatus.DONE);
            job.setStorageKey(storageKey);
            job.setDownloadUrl(downloadUrl);
            job.setRowCount(tasks.size());
            job.setCompletedAt(Instant.now());
            job.setExpiresAt(Instant.now().plus(Duration.ofHours(24)));
            exportJobRepository.save(job);

            webSocketService.sendToUser(
                job.getRequestedBy().getId(),
                "/queue/notifications",
                Map.of(
                    "type", "export.completed",
                    "jobId", job.getId(),
                    "downloadUrl", downloadUrl,
                    "message", "Export hoàn thành! File sẵn sàng tải về."
                )
            );

        } catch (Exception e) {
            log.error("[Export] Job {} failed: {}", job.getId(), e.getMessage(), e);
            job.setStatus(ExportJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            exportJobRepository.save(job);
        }
    }

    // ── Scheduled Cleanup ─────────────────────────────────────────────────────

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanExpiredExports() {
        List<ExportJob> expired = exportJobRepository.findByStatusAndExpiresAtBefore(
            ExportJobStatus.DONE, Instant.now());

        expired.forEach(job -> {
            try {
                if (job.getStorageKey() != null) {
                    minioStorageService.deleteFile(job.getStorageKey());
                }
                job.setStatus(ExportJobStatus.EXPIRED);
                exportJobRepository.save(job);
            } catch (Exception e) {
                log.warn("[Export] Failed to clean job {}: {}", job.getId(), e.getMessage());
            }
        });

        if (!expired.isEmpty()) {
            log.info("[Export] Cleaned {} expired export jobs", expired.size());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Task> getTasksForExport(UUID projectId, ExportScope scope, UUID sprintId) {
        if (scope == ExportScope.SPRINT && sprintId != null) {
            return taskRepository.findAll().stream()
                .filter(t -> projectId.equals(t.getProject().getId())
                    && t.getDeletedAt() == null
                    && t.getSprint() != null
                    && sprintId.equals(t.getSprint().getId()))
                .toList();
        }
        return taskRepository.findAll().stream()
            .filter(t -> projectId.equals(t.getProject().getId()) && t.getDeletedAt() == null)
            .toList();
    }

    private ResponseEntity<?> buildSyncExportResponse(List<Task> tasks, Project project,
                                                        ExportFormat format) {
        byte[] fileBytes;
        String fileName;
        String contentType;

        if (format == ExportFormat.EXCEL) {
            fileBytes = excelExportService.exportTasksToExcel(tasks, project);
            fileName = "tasks-" + project.getProjectKey() + "-" + LocalDate.now() + ".xlsx";
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else {
            fileBytes = pdfExportService.exportTasksToPdf(tasks, project);
            fileName = "tasks-" + project.getProjectKey() + "-" + LocalDate.now() + ".pdf";
            contentType = "application/pdf";
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"")
            .body(fileBytes);
    }
}
