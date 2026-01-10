package com.shop.ecommerceengine.cart.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

/**
 * Exception thrown when a cart item is not found.
 */
public class CartItemNotFoundException extends BaseCustomException {

    public CartItemNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND");
    }

    public CartItemNotFoundException(UUID userId, UUID productId) {
        super(
                String.format("Cart item not found for user %s, product %s", userId, productId),
                HttpStatus.NOT_FOUND,
                "CART_ITEM_NOT_FOUND",
                Map.of(
                        "userId", userId.toString(),
                        "productId", productId.toString()
                )
        );
    }
}
