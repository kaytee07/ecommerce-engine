package com.shop.ecommerceengine.inventory.service;

import com.shop.ecommerceengine.cart.service.InventoryServiceInterface;
import com.shop.ecommerceengine.inventory.dto.InventoryDTO;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Adapter that implements InventoryServiceInterface for use by cart module.
 * This bridges the inventory module to the cart module without tight coupling.
 */
@Service
public class InventoryServiceAdapter implements InventoryServiceInterface {

    private final InventoryService inventoryService;

    public InventoryServiceAdapter(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public boolean checkAvailability(UUID productId, int quantity) {
        return inventoryService.checkAvailability(productId, quantity);
    }

    @Override
    public int getAvailableQuantity(UUID productId) {
        try {
            InventoryDTO inventory = inventoryService.getInventory(productId);
            return inventory.availableQuantity();
        } catch (Exception e) {
            // If inventory doesn't exist, return 0
            return 0;
        }
    }
}
