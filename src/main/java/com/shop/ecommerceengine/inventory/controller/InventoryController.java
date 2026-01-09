package com.shop.ecommerceengine.inventory.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.inventory.dto.InventoryDTO;
import com.shop.ecommerceengine.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Storefront controller for inventory operations.
 * Public read-only endpoints for checking stock availability.
 */
@RestController
@RequestMapping("/api/v1/store/inventory")
@Tag(name = "Inventory", description = "Storefront inventory availability endpoints")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Get inventory for a product.
     */
    @GetMapping("/{productId}")
    @Operation(summary = "Get product inventory", description = "Returns stock information for a product")
    public ResponseEntity<ApiResponse<InventoryDTO>> getInventory(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId) {

        InventoryDTO inventory = inventoryService.getInventory(productId);
        return ResponseEntity.ok(ApiResponse.success(inventory));
    }

    /**
     * Check if requested quantity is available for a product.
     */
    @GetMapping("/{productId}/check")
    @Operation(summary = "Check availability", description = "Checks if requested quantity is available")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAvailability(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            @Parameter(description = "Requested quantity")
            @RequestParam int quantity) {

        boolean available = inventoryService.checkAvailability(productId, quantity);

        Map<String, Object> result = Map.of(
                "productId", productId.toString(),
                "requestedQuantity", quantity,
                "available", available
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Get inventory for multiple products (batch).
     */
    @PostMapping("/batch")
    @Operation(summary = "Get inventory batch", description = "Returns stock information for multiple products")
    public ResponseEntity<ApiResponse<List<InventoryDTO>>> getInventoryBatch(
            @Parameter(description = "List of product UUIDs")
            @RequestBody List<UUID> productIds) {

        List<InventoryDTO> inventories = inventoryService.getInventoryBatch(productIds);
        return ResponseEntity.ok(ApiResponse.success(inventories));
    }
}
