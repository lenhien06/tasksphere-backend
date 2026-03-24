package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.AttachmentResponse;
import com.zone.tasksphere.dto.response.PreviewUrlResponse;
import com.zone.tasksphere.dto.response.UploadJobResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface AttachmentService {

    AttachmentResponse upload(UUID projectId, UUID taskId, MultipartFile file, UUID currentUserId);

    /** Initiates an async upload — returns 202 + jobId immediately */
    UploadJobResponse initiateAsyncUpload(UUID projectId, UUID taskId, MultipartFile file, UUID currentUserId);
    AttachmentResponse uploadCommentAttachment(UUID projectId, UUID taskId, UUID commentId, MultipartFile file, UUID currentUserId);

    /** Polls the status of an async upload job */
    UploadJobResponse getJobStatus(UUID jobId, UUID currentUserId);

    List<AttachmentResponse> getAttachments(UUID projectId, UUID taskId, UUID currentUserId);
    List<AttachmentResponse> getCommentAttachments(UUID projectId, UUID taskId, UUID commentId, UUID currentUserId);

    /** Returns full AttachmentResponse including a fresh previewUrl (TTL 15min) */
    AttachmentResponse getPreviewUrl(UUID attachmentId, UUID currentUserId);

    /** Returns a lightweight PreviewUrlResponse with previewUrl + expiresAt */
    PreviewUrlResponse getPreviewUrlOnly(UUID attachmentId, UUID currentUserId);

    void deleteAttachment(UUID attachmentId, UUID currentUserId);
}
