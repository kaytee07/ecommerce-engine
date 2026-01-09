package com.shop.ecommerceengine.catalog.service;

import com.shop.ecommerceengine.common.config.S3Config;
import com.shop.ecommerceengine.common.exception.ImageUploadException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for image processing and S3/MinIO upload.
 * Uses Thumbnailator for resizing and CircuitBreaker for resilient uploads.
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Image sizes: width x height
    private static final int THUMB_WIDTH = 150;
    private static final int THUMB_HEIGHT = 150;
    private static final int MEDIUM_WIDTH = 400;
    private static final int MEDIUM_HEIGHT = 400;
    private static final int LARGE_WIDTH = 800;
    private static final int LARGE_HEIGHT = 800;

    private static final double IMAGE_QUALITY = 0.85;

    private final MinioClient minioClient;
    private final S3Config s3Config;

    public ImageService(MinioClient minioClient, S3Config s3Config) {
        this.minioClient = minioClient;
        this.s3Config = s3Config;
    }

    /**
     * Upload a product image with 3 size variants.
     * Returns a map of size -> URL.
     */
    @CircuitBreaker(name = "s3Upload", fallbackMethod = "uploadImageFallback")
    public Map<String, String> uploadProductImage(UUID productId, MultipartFile file) {
        log.info("Uploading image for product: {}", productId);

        // Validate file
        validateFile(file);

        try {
            ensureBucketExists();

            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String baseName = productId.toString() + "/" + UUID.randomUUID();

            Map<String, String> urls = new HashMap<>();

            // Process and upload each size
            byte[] thumbBytes = resizeImage(file.getInputStream(), THUMB_WIDTH, THUMB_HEIGHT, extension);
            String thumbKey = baseName + "-thumb." + extension;
            uploadToS3(thumbKey, thumbBytes, file.getContentType());
            urls.put("thumbnail", buildUrl(thumbKey));

            byte[] mediumBytes = resizeImage(file.getInputStream(), MEDIUM_WIDTH, MEDIUM_HEIGHT, extension);
            String mediumKey = baseName + "-medium." + extension;
            uploadToS3(mediumKey, mediumBytes, file.getContentType());
            urls.put("medium", buildUrl(mediumKey));

            byte[] largeBytes = resizeImage(file.getInputStream(), LARGE_WIDTH, LARGE_HEIGHT, extension);
            String largeKey = baseName + "-large." + extension;
            uploadToS3(largeKey, largeBytes, file.getContentType());
            urls.put("large", buildUrl(largeKey));

            // Also upload original
            String originalKey = baseName + "-original." + extension;
            uploadToS3(originalKey, file.getBytes(), file.getContentType());
            urls.put("original", buildUrl(originalKey));

            log.info("Successfully uploaded {} image variants for product {}", urls.size(), productId);
            return urls;

        } catch (IOException e) {
            log.error("Failed to process image for product {}: {}", productId, e.getMessage());
            throw ImageUploadException.processingFailed(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to upload image for product {}: {}", productId, e.getMessage());
            throw ImageUploadException.uploadFailed(e.getMessage());
        }
    }

    /**
     * Fallback method when S3 upload fails due to circuit breaker.
     */
    public Map<String, String> uploadImageFallback(UUID productId, MultipartFile file, Throwable t) {
        log.error("S3 upload circuit breaker triggered for product {}: {}", productId, t.getMessage());
        throw ImageUploadException.uploadFailed("Service temporarily unavailable. Please try again later.");
    }

    /**
     * Delete all image variants for a product.
     */
    @CircuitBreaker(name = "s3Delete", fallbackMethod = "deleteImageFallback")
    public void deleteProductImages(UUID productId, String imageBaseName) {
        log.info("Deleting images for product: {}", productId);

        try {
            String[] suffixes = {"-thumb", "-medium", "-large", "-original"};
            String extension = getFileExtension(imageBaseName);

            for (String suffix : suffixes) {
                String key = imageBaseName.replace("-original", suffix);
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(s3Config.getBucket())
                                .object(key)
                                .build()
                );
            }

            log.info("Successfully deleted images for product {}", productId);
        } catch (Exception e) {
            log.error("Failed to delete images for product {}: {}", productId, e.getMessage());
            // Don't throw - deletion failures shouldn't block product operations
        }
    }

    /**
     * Fallback method when S3 delete fails.
     */
    public void deleteImageFallback(UUID productId, String imageBaseName, Throwable t) {
        log.error("S3 delete circuit breaker triggered for product {}: {}", productId, t.getMessage());
        // Deletion failures are logged but don't throw - could be cleaned up by a scheduled job
    }

    /**
     * Validate uploaded file.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ImageUploadException.processingFailed("No file provided");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw ImageUploadException.fileTooLarge(file.getSize(), MAX_FILE_SIZE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw ImageUploadException.invalidFormat(contentType);
        }
    }

    /**
     * Resize image using Thumbnailator.
     */
    private byte[] resizeImage(InputStream inputStream, int width, int height, String format) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Thumbnails.of(inputStream)
                .size(width, height)
                .keepAspectRatio(true)
                .outputQuality(IMAGE_QUALITY)
                .outputFormat(format.equals("jpg") ? "jpeg" : format)
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    /**
     * Upload bytes to S3/MinIO.
     */
    private void uploadToS3(String key, byte[] data, String contentType) throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(s3Config.getBucket())
                        .object(key)
                        .stream(inputStream, data.length, -1)
                        .contentType(contentType)
                        .build()
        );

        log.debug("Uploaded {} to S3 bucket {}", key, s3Config.getBucket());
    }

    /**
     * Ensure the bucket exists, create if not.
     */
    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(s3Config.getBucket())
                        .build()
        );

        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(s3Config.getBucket())
                            .build()
            );
            log.info("Created S3 bucket: {}", s3Config.getBucket());
        }
    }

    /**
     * Build public URL for an object.
     */
    private String buildUrl(String key) {
        return String.format("%s/%s/%s", s3Config.getEndpoint(), s3Config.getBucket(), key);
    }

    /**
     * Extract file extension from filename.
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "jpg";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return ext.equals("jpeg") ? "jpg" : ext;
    }
}
