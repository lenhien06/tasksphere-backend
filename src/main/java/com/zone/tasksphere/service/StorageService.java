package com.zone.tasksphere.service;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * Abstraction for file storage (S3, MinIO, local, etc.)
 */
public interface StorageService {

    /**
     * Upload a file and return the storage key (S3 object key or path).
     */
    String upload(MultipartFile file, String folder);

    /**
     * Generate a presigned URL for downloading a file.
     * @param storageKey  the S3 key returned by upload()
     * @param ttl         how long the URL is valid
     * @return presigned URL string
     */
    String generatePresignedUrl(String storageKey, Duration ttl);

    /**
     * Permanently delete a file from storage.
     */
    void delete(String storageKey);
}
