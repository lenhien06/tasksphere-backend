package com.zone.tasksphere.component;

import com.zone.tasksphere.entity.Attachment;
import com.zone.tasksphere.entity.UploadJob;
import com.zone.tasksphere.entity.enums.AttachmentType;
import com.zone.tasksphere.entity.enums.UploadJobStatus;
import com.zone.tasksphere.repository.AttachmentRepository;
import com.zone.tasksphere.repository.UploadJobRepository;
import com.zone.tasksphere.service.AttachmentProcessor;
import com.zone.tasksphere.service.ClamAvService;
import com.zone.tasksphere.service.impl.MinioStorageService;
import com.zone.tasksphere.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentUploadScheduler {

    private final UploadJobRepository uploadJobRepository;
    private final AttachmentProcessor attachmentProcessor;

    /**
     * Chạy mỗi 5 giây để xử lý các file bị sót (nếu có lỗi trong quá trình xử lý ngay lập tức)
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingUploads() {
        List<UploadJob> pendingJobs = uploadJobRepository.findByStatus(UploadJobStatus.PENDING);
        if (pendingJobs.isEmpty()) return;

        log.info("[UploadScheduler] Cleaning up {} orphaned pending jobs", pendingJobs.size());

        for (UploadJob job : pendingJobs) {
            try {
                attachmentProcessor.processJob(job);
            } catch (Exception e) {
                log.error("[UploadScheduler] Critical error job {}: {}", job.getId(), e.getMessage());
            }
        }
    }
}
