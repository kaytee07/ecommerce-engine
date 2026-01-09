package com.shop.ecommerceengine.inventory.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

/**
 * Exception thrown when there is insufficient stock for an operation.
 */
public class InsufficientStockException extends BaseCustomException {

    public InsufficientStockException(String message) {
        super(message, HttpStatus.CONFLICT, "INSUFFICIENT_STOCK");
    }

    public InsufficientStockException(UUID productId, int requested, int available) {
        super(
                String.format("Insufficient stock for product %s. Requested: %d, Available: %d",
                        productId, requested, available),
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK",
                Map.of(
                        "productId", productId.toString(),
                        "requested", requested,
                        "available", available
                )
        );
    }
}
