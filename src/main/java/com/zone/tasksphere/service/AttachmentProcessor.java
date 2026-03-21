package com.zone.tasksphere.service;

import com.zone.tasksphere.entity.Attachment;
import com.zone.tasksphere.entity.UploadJob;
import com.zone.tasksphere.entity.enums.AttachmentType;
import com.zone.tasksphere.entity.enums.UploadJobStatus;
import com.zone.tasksphere.repository.AttachmentRepository;
import com.zone.tasksphere.repository.UploadJobRepository;
import com.zone.tasksphere.service.impl.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentProcessor {

    private final UploadJobRepository uploadJobRepository;
    private final AttachmentRepository attachmentRepository;
    private final MinioStorageService minioStorageService;
    private final ClamAvService clamAvService;
    private final WebSocketService webSocketService;

    @Async("taskExecutor")
    @Transactional
    public void processUploadJobAsync(UUID jobId) {
        UploadJob job = uploadJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != UploadJobStatus.PENDING) return;

        log.info("[Processor] Starting immediate processing for job: {}", jobId);
        processJob(job);
    }

    public void processJob(UploadJob job) {
        job.setStatus(UploadJobStatus.SCANNING);
        uploadJobRepository.save(job);

        try (var inputStream = minioStorageService.getFile(job.getTempStorageKey())) {
            // 1. Quét virus
            if (!clamAvService.isClean(inputStream)) {
                markAsFailed(job, "Phát hiện mã độc trong file");
                return;
            }

            // 2. Chuyển sang storage chính
            String finalKey = minioStorageService.moveFile(
                job.getTempStorageKey(),
                job.getTask().getProject().getId().toString(),
                job.getTask().getId().toString()
            );

            // 3. Tạo Attachment
            Attachment attachment = Attachment.builder()
                .task(job.getTask())
                .uploadedBy(job.getUploadedBy())
                .originalFilename(job.getOriginalFileName())
                .storedFilename(finalKey.substring(finalKey.lastIndexOf('/') + 1))
                .s3Key(finalKey)
                .fileSize(job.getFileSize())
                .contentType(job.getMimeType())
                .attachmentType(resolveAttachmentType(job.getMimeType()))
                .build();

            attachmentRepository.save(attachment);

            // 4. Hoàn tất
            job.setStatus(UploadJobStatus.DONE);
            job.setCompletedAt(Instant.now());
            uploadJobRepository.save(job);

            // 5. Push WS
            webSocketService.sendToUser(
                job.getUploadedBy().getId(),
                "/queue/notifications",
                Map.of(
                    "type", "attachment.uploaded",
                    "data", Map.of(
                        "jobId", job.getId(),
                        "taskId", job.getTask().getId(),
                        "fileName", job.getOriginalFileName()
                    )
                )
            );
            log.info("[Processor] Job {} completed successfully", job.getId());

        } catch (Exception e) {
            log.error("[Processor] Error job {}: {}", job.getId(), e.getMessage());
            markAsFailed(job, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    private void markAsFailed(UploadJob job, String error) {
        job.setStatus(UploadJobStatus.FAILED);
        job.setErrorMessage(error);
        job.setCompletedAt(Instant.now());
        uploadJobRepository.save(job);

        webSocketService.sendToUser(
            job.getUploadedBy().getId(),
            "/queue/notifications",
            Map.of("type", "attachment.scan_failed", "data", Map.of("jobId", job.getId(), "message", error))
        );
    }

    private AttachmentType resolveAttachmentType(String contentType) {
        if (contentType == null) return AttachmentType.OTHER;
        if (contentType.startsWith("image/")) return AttachmentType.IMAGE;
        if (contentType.contains("pdf") || contentType.contains("word") || contentType.contains("excel")) return AttachmentType.DOCUMENT;
        return AttachmentType.OTHER;
    }
}
