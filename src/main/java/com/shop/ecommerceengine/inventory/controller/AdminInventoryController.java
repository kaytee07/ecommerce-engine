package com.shop.ecommerceengine.inventory.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.service.UserService;
import com.shop.ecommerceengine.inventory.dto.InventoryAdminDTO;
import com.shop.ecommerceengine.inventory.dto.InventoryAdjustDTO;
import com.shop.ecommerceengine.inventory.dto.InventoryDTO;
import com.shop.ecommerceengine.inventory.service.InventoryAuditHelper;
import com.shop.ecommerceengine.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin controller for inventory management.
 * Requires ROLE_WAREHOUSE, ROLE_CONTENT_MANAGER, or ROLE_SUPER_ADMIN for access.
 */
@RestController
@RequestMapping("/api/v1/admin/inventory")
@Tag(name = "Admin Inventory", description = "Admin inventory management endpoints")
@PreAuthorize("hasAnyRole('WAREHOUSE', 'CONTENT_MANAGER', 'SUPER_ADMIN')")
public class AdminInventoryController {

    private static final Logger log = LoggerFactory.getLogger(AdminInventoryController.class);

    private final InventoryService inventoryService;
    private final InventoryAuditHelper auditHelper;
    private final UserService userService;

    public AdminInventoryController(InventoryService inventoryService,
                                    InventoryAuditHelper auditHelper,
                                    UserService userService) {
        this.inventoryService = inventoryService;
        this.auditHelper = auditHelper;
        this.userService = userService;
    }

    /**
     * Get inventory for a product (admin view).
     */
    @GetMapping("/{productId}")
    @Operation(summary = "Get product inventory (admin)", description = "Returns detailed stock information for admin")
    public ResponseEntity<ApiResponse<InventoryAdminDTO>> getInventory(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId) {

        log.debug("Admin fetching inventory for product: {}", productId);
        InventoryAdminDTO inventory = inventoryService.getInventoryForAdmin(productId);
        return ResponseEntity.ok(ApiResponse.success(inventory));
    }

    /**
     * Adjust stock for a product.
     */
    @PostMapping("/{productId}/adjust")
    @Operation(summary = "Adjust stock", description = "Adjusts stock quantity for a product")
    public ResponseEntity<ApiResponse<InventoryDTO>> adjustStock(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryAdjustDTO adjustment,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin adjusting stock for product {}: {} ({})",
                productId, adjustment.quantity(), adjustment.adjustmentType());

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();

        // Get current stock for audit
        InventoryAdminDTO before = inventoryService.getInventoryForAdmin(productId);

        InventoryDTO result = inventoryService.adjustStock(productId, adjustment, adminId, adminUsername);

        // Audit the adjustment
        auditHelper.auditStockAdjusted(
                adminId, adminUsername,
                productId,
                before.stockQuantity(), result.stockQuantity(),
                adjustment.adjustmentType(), adjustment.reason(),
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(result, "Stock adjusted successfully"));
    }

    /**
     * Create inventory for a new product.
     */
    @PostMapping("/{productId}")
    @Operation(summary = "Create inventory", description = "Creates inventory record for a product")
    public ResponseEntity<ApiResponse<InventoryDTO>> createInventory(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            @Parameter(description = "Initial stock quantity")
            @RequestParam(defaultValue = "0") int initialStock,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin creating inventory for product {}: initial stock {}", productId, initialStock);

        InventoryDTO result = inventoryService.createInventory(productId, initialStock);

        // Audit the creation
        UUID adminId = getAdminId(authentication);
        auditHelper.auditInventoryCreated(
                adminId, authentication.getName(),
                productId, initialStock,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(result, "Inventory created successfully"));
    }

    /**
     * Find products with low stock.
     */
    @GetMapping("/low-stock")
    @Operation(summary = "Find low stock", description = "Returns products with stock below threshold")
    public ResponseEntity<ApiResponse<List<InventoryDTO>>> findLowStock(
            @Parameter(description = "Stock threshold")
            @RequestParam(defaultValue = "5") int threshold) {

        log.debug("Admin finding low stock products (threshold: {})", threshold);
        List<InventoryDTO> lowStock = inventoryService.findLowStockProducts(threshold);
        return ResponseEntity.ok(ApiResponse.success(lowStock));
    }

    /**
     * Find products that are out of stock.
     */
    @GetMapping("/out-of-stock")
    @Operation(summary = "Find out of stock", description = "Returns products that are out of stock")
    public ResponseEntity<ApiResponse<List<InventoryDTO>>> findOutOfStock() {

        log.debug("Admin finding out of stock products");
        List<InventoryDTO> outOfStock = inventoryService.findOutOfStockProducts();
        return ResponseEntity.ok(ApiResponse.success(outOfStock));
    }

    /**
     * Reserve stock (for order processing).
     */
    @PostMapping("/{productId}/reserve")
    @Operation(summary = "Reserve stock", description = "Reserves stock for a pending order")
    public ResponseEntity<ApiResponse<InventoryDTO>> reserveStock(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            @Parameter(description = "Quantity to reserve")
            @RequestParam int quantity,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin reserving {} units for product {}", quantity, productId);

        InventoryDTO result = inventoryService.reserveStock(productId, quantity);

        // Audit the reservation
        UUID adminId = getAdminId(authentication);
        auditHelper.auditStockReserved(
                adminId, authentication.getName(),
                productId, quantity,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(result, "Stock reserved successfully"));
    }

    /**
     * Release reserved stock (for cancelled orders).
     */
    @PostMapping("/{productId}/release")
    @Operation(summary = "Release reserved stock", description = "Releases previously reserved stock")
    public ResponseEntity<ApiResponse<InventoryDTO>> releaseStock(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            @Parameter(description = "Quantity to release")
            @RequestParam int quantity,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin releasing {} units for product {}", quantity, productId);

        InventoryDTO result = inventoryService.releaseReservedStock(productId, quantity);

        // Audit the release
        UUID adminId = getAdminId(authentication);
        auditHelper.auditStockReleased(
                adminId, authentication.getName(),
                productId, quantity,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(result, "Stock released successfully"));
    }

    /**
     * Commit reserved stock (for fulfilled orders).
     */
    @PostMapping("/{productId}/commit")
    @Operation(summary = "Commit reserved stock", description = "Commits reserved stock when order is fulfilled")
    public ResponseEntity<ApiResponse<InventoryDTO>> commitStock(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            @Parameter(description = "Quantity to commit")
            @RequestParam int quantity,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin committing {} units for product {}", quantity, productId);

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();

        InventoryDTO result = inventoryService.commitReservedStock(productId, quantity, adminId, adminUsername);

        return ResponseEntity.ok(ApiResponse.success(result, "Stock committed successfully"));
    }

    // ===================== Private helpers =====================

    private UUID getAdminId(Authentication authentication) {
        try {
            return userService.getUserByUsername(authentication.getName()).id();
        } catch (Exception e) {
            log.warn("Could not get admin user ID: {}", e.getMessage());
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
