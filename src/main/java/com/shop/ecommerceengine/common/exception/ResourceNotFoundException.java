package com.shop.ecommerceengine.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends BaseCustomException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(
                String.format("Resource not found: %s with %s %s", resourceName, fieldName, fieldValue),
                HttpStatus.NOT_FOUND,
                ERROR_CODE,
                Map.of(
                        "resource", resourceName,
                        "field", fieldName,
                        "value", fieldValue.toString()
                )
        );
    }

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, ERROR_CODE);
    }
}
