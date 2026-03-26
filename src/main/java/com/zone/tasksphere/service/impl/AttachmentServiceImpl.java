package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.AttachmentResponse;
import com.zone.tasksphere.dto.response.PreviewUrlResponse;
import com.zone.tasksphere.dto.response.UploadJobResponse;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.AttachmentType;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.UploadJobStatus;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.FileTooLargeException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.exception.UnsupportedFileTypeException;
import com.zone.tasksphere.repository.AttachmentRepository;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UploadJobRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.repository.CommentRepository;
import com.zone.tasksphere.service.AttachmentService;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.ClamAvService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MinioStorageService minioStorageService;
    private final ClamAvService clamAvService;
    private final UploadJobRepository uploadJobRepository;
    private final CommentRepository commentRepository;
    private final ActivityLogService activityLogService;

    private final Tika tika = new Tika();

    @Value("${app.attachment.max-file-size:26214400}")
    private long maxFileSize;

    @Value("${app.attachment.allowed-mime-types:image/jpeg,image/png,image/gif,image/webp,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/zip,text/plain,text/csv}")
    private List<String> allowedMimeTypes;

    // ── UPLOAD (sync with MIME validation + optional ClamAV) ────────────────

    @Override
    public AttachmentResponse upload(UUID projectId, UUID taskId, MultipartFile file, UUID currentUserId) {
        Task task = getTaskInProject(taskId, projectId);
        validateMember(projectId, currentUserId);
        User uploader = getUser(currentUserId);

        // 413 — file size
        if (file.getSize() > maxFileSize) {
            throw new FileTooLargeException(
                "File vượt quá giới hạn 25MB. Kích thước thực: " + formatSize(file.getSize()));
        }

        // Detect real MIME type via magic bytes
        String detectedMime = detectMimeType(file);

        // 415 — check detected MIME type
        if (!allowedMimeTypes.contains(detectedMime)) {
            throw new UnsupportedFileTypeException("Định dạng file không được phép: " + detectedMime);
        }

        // Check MIME mismatch (file disguise detection)
        String declaredMime = file.getContentType();
        if (declaredMime != null && !detectedMime.equals(declaredMime)) {
            log.warn("[Attachment] MIME mismatch: declared={}, actual={}, file={}",
                     declaredMime, detectedMime, file.getOriginalFilename());
            throw new UnsupportedFileTypeException("File type không khớp với nội dung thực tế");
        }

        // Optional ClamAV scan
        try {
            if (!clamAvService.isClean(file.getInputStream())) {
                throw new BusinessRuleException("FILE_003: File bị từ chối vì phát hiện mã độc");
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException e) {
            log.warn("[Attachment] Could not read file for scanning: {}", e.getMessage());
        }

        String s3Key;
        try {
            s3Key = minioStorageService.uploadFile(file, projectId.toString(), taskId.toString());
        } catch (Exception e) {
            log.error("[Attachment] Storage unavailable on upload: {}", e.getMessage(), e);
            throw new StructuredApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_UNAVAILABLE",
                    "Storage service tạm thời không khả dụng. Vui lòng thử lại sau.");
        }

        Attachment attachment = Attachment.builder()
            .task(task)
            .uploadedBy(uploader)
            .originalFilename(file.getOriginalFilename())
            .storedFilename(s3Key.substring(s3Key.lastIndexOf('/') + 1))
            .s3Key(s3Key)
            .fileSize(file.getSize())
            .contentType(detectedMime)
            .attachmentType(resolveAttachmentType(detectedMime))
            .build();

        attachment = attachmentRepository.save(attachment);
        log.info("Attachment saved: {} for task {}", attachment.getId(), taskId);
        logActivity(projectId, currentUserId, EntityType.ATTACHMENT, attachment.getId(),
                ActionType.ATTACHMENT_UPLOADED, null, attachment.getOriginalFilename());

        return toResponse(attachment);
    }

    // ── ASYNC UPLOAD — returns 202 + jobId ───────────────────────────────────

    @Override
    public UploadJobResponse initiateAsyncUpload(UUID projectId, UUID taskId, MultipartFile file, UUID currentUserId) {
        Task task = getTaskInProject(taskId, projectId);
        validateMember(projectId, currentUserId);
        User uploader = getUser(currentUserId);

        // Basic validations before async
        if (file.getSize() > maxFileSize) {
            throw new FileTooLargeException("File vượt quá giới hạn 25MB.");
        }

        String detectedMime = detectMimeType(file);
        if (!allowedMimeTypes.contains(detectedMime)) {
            throw new UnsupportedFileTypeException("Định dạng file không được phép: " + detectedMime);
        }

        // Upload to temp storage key first
        String tempKey;
        try {
            tempKey = minioStorageService.uploadFile(file, projectId.toString(), "tmp-" + taskId.toString());
        } catch (Exception e) {
            log.error("[Attachment] Storage unavailable on async-init upload: {}", e.getMessage(), e);
            throw new StructuredApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_UNAVAILABLE",
                    "Storage service tạm thời không khả dụng. Vui lòng thử lại sau.");
        }

        UploadJob job = UploadJob.builder()
            .task(task)
            .uploadedBy(uploader)
            .originalFileName(file.getOriginalFilename())
            .fileSize(file.getSize())
            .mimeType(detectedMime)
            .tempStorageKey(tempKey)
            .status(UploadJobStatus.PENDING)
            .build();

        job = uploadJobRepository.save(job);

        return UploadJobResponse.builder()
            .jobId(job.getId())
            .status(UploadJobStatus.PENDING)
            .fileName(file.getOriginalFilename())
            .message("File đang được xử lý")
            .build();
    }

    // ── GET JOB STATUS ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UploadJobResponse getJobStatus(UUID jobId, UUID currentUserId) {
        UploadJob job = uploadJobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Upload job not found: " + jobId));

        if (!job.getUploadedBy().getId().equals(currentUserId)) {
            throw new Forbidden("Bạn không có quyền xem job này");
        }

        UploadJobResponse.UploadJobResponseBuilder builder = UploadJobResponse.builder()
            .jobId(job.getId())
            .status(job.getStatus())
            .fileName(job.getOriginalFileName())
            .errorMessage(job.getErrorMessage());

        if (job.getStatus() == UploadJobStatus.DONE) {
            builder.message("File đã sẵn sàng");
        } else if (job.getStatus() == UploadJobStatus.FAILED) {
            builder.message(job.getErrorMessage());
        } else {
            builder.message("Đang xử lý...");
        }

        return builder.build();
    }

    // ── LIST ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> getAttachments(UUID projectId, UUID taskId, UUID currentUserId) {
        getTaskInProject(taskId, projectId);
        validateMembership(projectId, currentUserId);

        return attachmentRepository.findByTaskIdAndCommentIsNullOrderByCreatedAtDesc(taskId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public AttachmentResponse uploadCommentAttachment(UUID projectId, UUID taskId, UUID commentId, MultipartFile file, UUID currentUserId) {
        Task task = getTaskInProject(taskId, projectId);
        Comment comment = getCommentInTask(commentId, taskId);
        validateMember(projectId, currentUserId);
        User uploader = getUser(currentUserId);

        if (file.getSize() > maxFileSize) {
            throw new FileTooLargeException(
                "File vượt quá giới hạn 25MB. Kích thước thực: " + formatSize(file.getSize()));
        }

        String detectedMime = detectMimeType(file);
        if (!allowedMimeTypes.contains(detectedMime)) {
            throw new UnsupportedFileTypeException("Định dạng file không được phép: " + detectedMime);
        }

        String declaredMime = file.getContentType();
        if (declaredMime != null && !detectedMime.equals(declaredMime)) {
            log.warn("[CommentAttachment] MIME mismatch: declared={}, actual={}, file={}",
                    declaredMime, detectedMime, file.getOriginalFilename());
            throw new UnsupportedFileTypeException("File type không khớp với nội dung thực tế");
        }

        try {
            if (!clamAvService.isClean(file.getInputStream())) {
                throw new BusinessRuleException("FILE_003: File bị từ chối vì phát hiện mã độc");
            }
        } catch (BusinessRuleException e) {
            throw e;
        } catch (IOException e) {
            log.warn("[CommentAttachment] Could not read file for scanning: {}", e.getMessage());
        }

        String s3Key;
        try {
            s3Key = minioStorageService.uploadFile(file, projectId.toString(), taskId.toString());
        } catch (Exception e) {
            log.error("[CommentAttachment] Storage unavailable on upload: {}", e.getMessage(), e);
            throw new StructuredApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "STORAGE_UNAVAILABLE",
                    "Storage service tạm thời không khả dụng. Vui lòng thử lại sau.");
        }

        Attachment attachment = Attachment.builder()
            .task(task)
            .comment(comment)
            .uploadedBy(uploader)
            .originalFilename(file.getOriginalFilename())
            .storedFilename(s3Key.substring(s3Key.lastIndexOf('/') + 1))
            .s3Key(s3Key)
            .fileSize(file.getSize())
            .contentType(detectedMime)
            .attachmentType(resolveAttachmentType(detectedMime))
            .build();

        attachment = attachmentRepository.save(attachment);
        logActivity(projectId, currentUserId, EntityType.ATTACHMENT, attachment.getId(),
                ActionType.ATTACHMENT_UPLOADED, null, attachment.getOriginalFilename());
        return toResponse(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentResponse> getCommentAttachments(UUID projectId, UUID taskId, UUID commentId, UUID currentUserId) {
        getTaskInProject(taskId, projectId);
        getCommentInTask(commentId, taskId);
        validateMembership(projectId, currentUserId);

        return attachmentRepository.findByCommentIdOrderByCreatedAtDesc(commentId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    // ── PREVIEW URL ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AttachmentResponse getPreviewUrl(UUID attachmentId, UUID currentUserId) {
        Attachment attachment = getAttachment(attachmentId);
        validateMembership(attachment.getTask().getProject().getId(), currentUserId);

        if (!minioStorageService.isPreviewable(attachment.getContentType())) {
            throw new BusinessRuleException(
                "File này không hỗ trợ preview: " + attachment.getContentType());
        }
        return toResponse(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public PreviewUrlResponse getPreviewUrlOnly(UUID attachmentId, UUID currentUserId) {
        Attachment attachment = getAttachment(attachmentId);
        validateMembership(attachment.getTask().getProject().getId(), currentUserId);

        if (!minioStorageService.isPreviewable(attachment.getContentType())) {
            throw new BusinessRuleException(
                "File này không hỗ trợ preview: " + attachment.getContentType());
        }

        return PreviewUrlResponse.builder()
            .previewUrl(minioStorageService.generatePreviewUrl(attachment.getS3Key()))
            .mimeType(attachment.getContentType())
            .expiresAt(Instant.now().plusSeconds(900))
            .build();
    }

    // ── DELETE ──────────────────────────────────────────────────────

    @Override
    public void deleteAttachment(UUID attachmentId, UUID currentUserId) {
        Attachment attachment = getAttachment(attachmentId);
        UUID projectId = attachment.getTask().getProject().getId();

        boolean isUploader = attachment.getUploadedBy().getId().equals(currentUserId);
        boolean isPM = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .map(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
            .orElse(false);

        if (!isUploader && !isPM) {
            throw new Forbidden("Chỉ người upload hoặc PM mới được xóa file này");
        }

        attachment.setDeletedAt(Instant.now());
        attachmentRepository.save(attachment);
        logActivity(projectId, currentUserId, EntityType.ATTACHMENT, attachment.getId(),
                ActionType.ATTACHMENT_DELETED, attachment.getOriginalFilename(), null);

        deleteFromMinioAsync(attachment.getS3Key());
    }

    // ── Async ────────────────────────────────────────────────────────

    @Async
    protected void deleteFromMinioAsync(String s3Key) {
        minioStorageService.deleteFile(s3Key);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private String detectMimeType(MultipartFile file) {
        try {
            return tika.detect(file.getInputStream(), file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("[Attachment] Could not detect MIME type: {}", e.getMessage());
            // Fallback to declared content type
            return file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        }
    }

    private Task getTaskInProject(UUID taskId, UUID projectId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    private Attachment getAttachment(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
            .filter(a -> a.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private Comment getCommentInTask(UUID commentId, UUID taskId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
        if (!comment.getTask().getId().equals(taskId)) {
            throw new NotFoundException("Comment không thuộc task này");
        }
        return comment;
    }

    private void validateMembership(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
    }

    private void validateMember(UUID projectId, UUID userId) {
        var member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
        if (member.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không có quyền upload file");
        }
    }

    private AttachmentType resolveAttachmentType(String contentType) {
        if (contentType == null) return AttachmentType.OTHER;
        if (contentType.startsWith("image/")) return AttachmentType.IMAGE;
        if (contentType.equals("application/pdf")
                || contentType.contains("word") || contentType.contains("spreadsheet")
                || contentType.contains("excel")) return AttachmentType.DOCUMENT;
        if (contentType.equals("application/zip")
                || contentType.equals("application/x-zip-compressed")) return AttachmentType.ARCHIVE;
        if (contentType.startsWith("text/")) return AttachmentType.CODE;
        return AttachmentType.OTHER;
    }

    private AttachmentResponse toResponse(Attachment a) {
        String downloadUrl = minioStorageService.generateDownloadUrl(a.getS3Key());
        boolean previewable = minioStorageService.isPreviewable(a.getContentType());
        String previewUrl = previewable ? minioStorageService.generatePreviewUrl(a.getS3Key()) : null;

        User u = a.getUploadedBy();
        return AttachmentResponse.builder()
            .id(a.getId())
            .fileName(a.getOriginalFilename())
            .fileSize(a.getFileSize())
            .mimeType(a.getContentType())
            .downloadUrl(downloadUrl)
            .previewUrl(previewUrl)
            .previewable(previewable)
            .uploadedBy(AttachmentResponse.UserSummary.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .build())
            .uploadedAt(a.getCreatedAt())
            .build();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void logActivity(UUID projectId, UUID actorId, EntityType entityType, UUID entityId,
                             ActionType action, String oldVal, String newVal) {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, entityType, entityId, action, oldVal, newVal, request);
        } catch (Exception e) {
            log.warn("Failed to log attachment activity: {}", e.getMessage());
        }
    }
}
