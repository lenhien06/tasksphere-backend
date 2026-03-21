package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.config.MinioConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * MinIO storage service — dùng AWS SDK v2 với S3-compatible API.
 *
 * Key format: attachments/{projectId}/{taskId}/{uuid}/{safeFilename}
 * Download URL TTL: 1h  (configurable via minio.presigned-url-expiry)
 * Preview  URL TTL: 15m (configurable via minio.preview-url-expiry)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final S3Client minioS3Client;
    private final S3Presigner minioS3Presigner;
    private final MinioConfig minioConfig;

    // ── Upload ────────────────────────────────────────────────────────

    /**
     * Upload file lên MinIO.
     * @return storageKey (dùng để gen presigned URL sau)
     */
    public String uploadFile(MultipartFile file, String projectId, String taskId) {
        String uuid = UUID.randomUUID().toString();
        String safeFileName = sanitizeFileName(file.getOriginalFilename());
        String storageKey = String.format("attachments/%s/%s/%s/%s",
            projectId, taskId, uuid, safeFileName);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(minioConfig.getBucketName())
                .key(storageKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

            minioS3Client.putObject(request,
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("[MinIO] Uploaded: {}", storageKey);
            return storageKey;

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    // ── Presigned URLs ────────────────────────────────────────────────

    /**
     * Upload raw bytes lên MinIO (dùng cho export jobs).
     * @return storageKey
     */
    public String uploadBytes(byte[] bytes, String storageKey, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(minioConfig.getBucketName())
            .key(storageKey)
            .contentType(contentType)
            .contentLength((long) bytes.length)
            .build();

        minioS3Client.putObject(request, RequestBody.fromBytes(bytes));
        log.info("[MinIO] Uploaded bytes: {}", storageKey);
        return storageKey;
    }

    /**
     * Gen presigned download URL — TTL 1h.
     * URL dạng: http://localhost:9000/tasksphere-files/attachments/...
     */
    public String generateDownloadUrl(String storageKey) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(minioConfig.getPresignedUrlExpiry()))
            .getObjectRequest(r -> r
                .bucket(minioConfig.getBucketName())
                .key(storageKey))
            .build();

        return minioS3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Gen presigned download URL với TTL tùy chỉnh.
     */
    public String generateDownloadUrl(String storageKey, Duration ttl) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(r -> r
                .bucket(minioConfig.getBucketName())
                .key(storageKey))
            .build();

        return minioS3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Gen presigned preview URL — TTL 15min.
     * Chỉ dùng cho image/* và application/pdf.
     */
    public String generatePreviewUrl(String storageKey) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(minioConfig.getPreviewUrlExpiry()))
            .getObjectRequest(r -> r
                .bucket(minioConfig.getBucketName())
                .key(storageKey))
            .build();

        return minioS3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // ── Download / Move ───────────────────────────────────────────────

    /**
     * Lấy nội dung file từ MinIO dưới dạng InputStream.
     */
    public InputStream getFile(String storageKey) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(minioConfig.getBucketName())
            .key(storageKey)
            .build();
        return minioS3Client.getObject(request);
    }

    /**
     * Copy file sang key mới (attachments/{projectId}/{taskId}/{uuid}/{filename})
     * rồi xóa key cũ. Dùng cho luồng: temp upload → scan → move to final.
     *
     * @return finalKey của file sau khi move
     */
    public String moveFile(String tempKey, String projectId, String taskId) {
        String fileName = tempKey.contains("/")
            ? tempKey.substring(tempKey.lastIndexOf('/') + 1)
            : tempKey;
        String finalKey = String.format("attachments/%s/%s/%s/%s",
            projectId, taskId, UUID.randomUUID(), fileName);

        minioS3Client.copyObject(CopyObjectRequest.builder()
            .sourceBucket(minioConfig.getBucketName())
            .sourceKey(tempKey)
            .destinationBucket(minioConfig.getBucketName())
            .destinationKey(finalKey)
            .build());

        deleteFile(tempKey);
        log.info("[MinIO] Moved {} → {}", tempKey, finalKey);
        return finalKey;
    }

    // ── Delete ────────────────────────────────────────────────────────

    /**
     * Xóa file khỏi MinIO.
     * Không throw — DB soft-delete vẫn thành công dù S3 lỗi.
     */
    public void deleteFile(String storageKey) {
        try {
            minioS3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(minioConfig.getBucketName())
                .key(storageKey)
                .build());
            log.info("[MinIO] Deleted: {}", storageKey);
        } catch (Exception e) {
            log.error("[MinIO] Failed to delete {}: {}", storageKey, e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** image/* và application/pdf có thể preview trực tiếp trên browser */
    public boolean isPreviewable(String contentType) {
        if (contentType == null) return false;
        return contentType.startsWith("image/")
            || MediaType.APPLICATION_PDF_VALUE.equals(contentType);
    }

    /** Xóa ký tự nguy hiểm, giữ lại chữ, số, dấu chấm, gạch ngang */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file_" + System.currentTimeMillis();
        }
        return fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
