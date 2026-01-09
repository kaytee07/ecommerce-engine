package com.shop.ecommerceengine.catalog.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when a requested product is not found.
 */
public class ProductNotFoundException extends BaseCustomException {

    private static final String ERROR_CODE = "PRODUCT_NOT_FOUND";

    public ProductNotFoundException(UUID productId) {
        super(String.format("Product not found with ID: %s", productId), HttpStatus.NOT_FOUND, ERROR_CODE);
        addDetail("productId", productId.toString());
    }

    public ProductNotFoundException(String slug) {
        super(String.format("Product not found with slug: %s", slug), HttpStatus.NOT_FOUND, ERROR_CODE);
        addDetail("slug", slug);
    }
}
