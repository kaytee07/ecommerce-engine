package com.shop.ecommerceengine.inventory.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

/**
 * Exception thrown when inventory record is not found for a product.
 */
public class InventoryNotFoundException extends BaseCustomException {

    public InventoryNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "INVENTORY_NOT_FOUND");
    }

    public InventoryNotFoundException(UUID productId) {
        super(
                String.format("Inventory not found for product: %s", productId),
                HttpStatus.NOT_FOUND,
                "INVENTORY_NOT_FOUND",
                Map.of("productId", productId.toString())
        );
    }
}
