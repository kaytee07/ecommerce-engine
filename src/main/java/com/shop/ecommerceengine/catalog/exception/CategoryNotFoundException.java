package com.shop.ecommerceengine.catalog.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when a requested category is not found.
 */
public class CategoryNotFoundException extends BaseCustomException {

    private static final String ERROR_CODE = "CATEGORY_NOT_FOUND";

    public CategoryNotFoundException(UUID categoryId) {
        super(String.format("Category not found with ID: %s", categoryId), HttpStatus.NOT_FOUND, ERROR_CODE);
        addDetail("categoryId", categoryId.toString());
    }

    public CategoryNotFoundException(String slug) {
        super(String.format("Category not found with slug: %s", slug), HttpStatus.NOT_FOUND, ERROR_CODE);
        addDetail("slug", slug);
    }
}
