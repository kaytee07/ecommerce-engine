package com.shop.ecommerceengine.common.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for S3-compatible object storage (MinIO).
 * Provides a MinioClient bean for image uploads.
 */
@Configuration
public class S3Config {

    @Value("${s3.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${s3.access-key:minioadmin}")
    private String accessKey;

    @Value("${s3.secret-key:minioadmin}")
    private String secretKey;

    @Value("${s3.bucket:ecommerce-images}")
    private String bucket;

    @Value("${s3.region:us-east-1}")
    private String region;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();
    }

    public String getBucket() {
        return bucket;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
