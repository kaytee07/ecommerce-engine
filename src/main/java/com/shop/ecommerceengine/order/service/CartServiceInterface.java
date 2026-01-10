package com.shop.ecommerceengine.order.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Interface for cart operations used by the Order module.
 * Provides cross-module isolation - Order module doesn't directly import Cart module.
 */
public interface CartServiceInterface {

    /**
     * Get cart items for a user.
     *
     * @param userId the user ID
     * @return list of cart items
     */
    List<CartItemSnapshot> getCartItems(UUID userId);

    /**
     * Check if cart is empty.
     *
     * @param userId the user ID
     * @return true if cart has no items
     */
    boolean isCartEmpty(UUID userId);

    /**
     * Clear all items from user's cart.
     *
     * @param userId the user ID
     */
    void clearCart(UUID userId);

    /**
     * Get total amount for user's cart.
     *
     * @param userId the user ID
     * @return total cart amount
     */
    BigDecimal getCartTotal(UUID userId);

    /**
     * Snapshot of a cart item for order creation.
     * Captures product details at checkout time.
     */
    record CartItemSnapshot(
            UUID productId,
            String productName,
            String sku,
            int quantity,
            BigDecimal priceAtAdd
    ) {
        public BigDecimal getSubtotal() {
            return priceAtAdd.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
