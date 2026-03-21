package com.zone.tasksphere.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * MinIO storage configuration.
 * Uses AWS SDK v2 with S3-compatible API — MinIO dùng S3 API hoàn toàn.
 *
 * Binds từ application.yml: minio.*
 * Env vars: MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET, MINIO_PUBLIC_URL
 */
@Configuration
@ConfigurationProperties(prefix = "minio")
@Data
@Slf4j
public class MinioConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    /** Public URL trả về FE — nếu MinIO sau nginx thì set domain thật */
    private String publicUrl;
    private long presignedUrlExpiry = 3600;  // seconds (1h)
    private long previewUrlExpiry   = 900;   // seconds (15min)

    /**
     * S3Client trỏ vào MinIO.
     * QUAN TRỌNG: pathStyleAccessEnabled(true) — bắt buộc với MinIO.
     * Region US_EAST_1 — MinIO không quan tâm region, dùng giá trị giả.
     */
    @Bean
    public S3Client minioS3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)     // BẮT BUỘC với MinIO
                .build())
            .build();
    }

    /**
     * S3Presigner dùng publicUrl để gen URL FE có thể access được.
     * Nếu MinIO đứng sau reverse proxy → publicUrl = domain public.
     */
    @Bean
    public S3Presigner minioS3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create(publicUrl))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }

    /**
     * Tạo bucket khi app start nếu chưa tồn tại.
     * Không throw — app vẫn start, chỉ log error nếu MinIO chưa sẵn sàng.
     */
    @Bean
    @DependsOn("minioS3Client")
    public Boolean ensureBucketExists(S3Client minioS3Client) {
        try {
            minioS3Client.headBucket(b -> b.bucket(bucketName));
            log.info("[MinIO] Bucket '{}' already exists", bucketName);
        } catch (NoSuchBucketException e) {
            minioS3Client.createBucket(b -> b.bucket(bucketName));
            log.info("[MinIO] Created bucket '{}'", bucketName);
        } catch (Exception e) {
            log.error("[MinIO] Cannot connect — bucket check skipped: {}", e.getMessage());
        }
        return true;
    }
}
