package com.shop.ecommerceengine.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when image upload or processing fails.
 */
public class ImageUploadException extends BaseCustomException {

    private static final String ERROR_CODE = "IMAGE_UPLOAD_FAILED";

    public ImageUploadException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, ERROR_CODE);
    }

    public ImageUploadException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, ERROR_CODE);
        initCause(cause);
    }

    public static ImageUploadException processingFailed(String reason) {
        ImageUploadException ex = new ImageUploadException("Image processing failed: " + reason);
        ex.addDetail("reason", reason);
        return ex;
    }

    public static ImageUploadException uploadFailed(String reason) {
        ImageUploadException ex = new ImageUploadException("Image upload failed: " + reason);
        ex.addDetail("reason", reason);
        return ex;
    }

    public static ImageUploadException invalidFormat(String format) {
        ImageUploadException ex = new ImageUploadException("Invalid image format: " + format);
        ex.addDetail("format", format);
        ex.addDetail("allowedFormats", "jpg, jpeg, png, gif, webp");
        return ex;
    }

    public static ImageUploadException fileTooLarge(long size, long maxSize) {
        ImageUploadException ex = new ImageUploadException(
                String.format("File size %d bytes exceeds maximum allowed %d bytes", size, maxSize)
        );
        ex.addDetail("fileSize", size);
        ex.addDetail("maxSize", maxSize);
        return ex;
    }
}
