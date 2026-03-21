package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.service.StorageService;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * @deprecated Replaced by {@link MinioStorageService}.
 * Kept as a stub to satisfy existing references during migration.
 * Do NOT inject this class — inject MinioStorageService directly.
 */
@Deprecated
public class S3StorageService implements StorageService {

    @Override
    public String upload(MultipartFile file, String folder) {
        throw new UnsupportedOperationException("Use MinioStorageService");
    }

    @Override
    public String generatePresignedUrl(String storageKey, Duration ttl) {
        throw new UnsupportedOperationException("Use MinioStorageService");
    }

    @Override
    public void delete(String storageKey) {
        throw new UnsupportedOperationException("Use MinioStorageService");
    }
}
