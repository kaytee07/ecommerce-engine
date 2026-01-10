package com.shop.ecommerceengine.cart.service;

import java.util.UUID;

/**
 * Interface for inventory operations used by cart module.
 * This ensures cross-module isolation - cart doesn't directly import inventory classes.
 */
public interface InventoryServiceInterface {

    /**
     * Check if requested quantity is available for a product.
     *
     * @param productId The product ID
     * @param quantity The requested quantity
     * @return true if quantity is available
     */
    boolean checkAvailability(UUID productId, int quantity);

    /**
     * Get available quantity for a product.
     *
     * @param productId The product ID
     * @return Available quantity (stock - reserved)
     */
    int getAvailableQuantity(UUID productId);
}
