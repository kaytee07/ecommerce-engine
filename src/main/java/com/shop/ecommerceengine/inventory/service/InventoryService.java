package com.shop.ecommerceengine.inventory.service;

import com.shop.ecommerceengine.inventory.dto.InventoryAdjustDTO;
import com.shop.ecommerceengine.inventory.dto.InventoryAdminDTO;
import com.shop.ecommerceengine.inventory.dto.InventoryDTO;
import com.shop.ecommerceengine.inventory.entity.InventoryAdjustmentEntity;
import com.shop.ecommerceengine.inventory.entity.InventoryEntity;
import com.shop.ecommerceengine.inventory.event.LowStockEvent;
import com.shop.ecommerceengine.inventory.exception.InsufficientStockException;
import com.shop.ecommerceengine.inventory.exception.InventoryNotFoundException;
import com.shop.ecommerceengine.inventory.mapper.InventoryMapper;
import com.shop.ecommerceengine.inventory.repository.InventoryAdjustmentRepository;
import com.shop.ecommerceengine.inventory.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for inventory management.
 * Handles stock operations with optimistic locking for concurrent access.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryMapper inventoryMapper;
    private final ApplicationEventPublisher eventPublisher;

    public InventoryService(InventoryRepository inventoryRepository,
                           InventoryAdjustmentRepository adjustmentRepository,
                           InventoryMapper inventoryMapper,
                           ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.inventoryMapper = inventoryMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Check if requested quantity is available for a product.
     */
    @Transactional(readOnly = true)
    public boolean checkAvailability(UUID productId, int quantity) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> inv.hasAvailable(quantity))
                .orElse(false);
    }

    /**
     * Get inventory for a product.
     */
    @Transactional(readOnly = true)
    public InventoryDTO getInventory(UUID productId) {
        InventoryEntity inventory = findInventoryOrThrow(productId);
        return inventoryMapper.toDTO(inventory);
    }

    /**
     * Get inventory for admin view.
     */
    @Transactional(readOnly = true)
    public InventoryAdminDTO getInventoryForAdmin(UUID productId) {
        InventoryEntity inventory = findInventoryOrThrow(productId);
        return inventoryMapper.toAdminDTO(inventory);
    }

    /**
     * Adjust stock quantity (for sales, restocks, corrections).
     * Uses optimistic locking (@Version) - throws ObjectOptimisticLockingFailureException on conflict.
     * Callers should implement retry logic if needed.
     */
    @Transactional
    public InventoryDTO adjustStock(UUID productId, InventoryAdjustDTO adjustment,
                                    UUID adminId, String adminUsername) {
        InventoryEntity inventory = findInventoryOrThrow(productId);
        int quantityBefore = inventory.getStockQuantity();
        int quantityChange = adjustment.quantity();
        int newQuantity = quantityBefore + quantityChange;

        // Validate we won't go negative
        if (newQuantity < 0) {
            throw new InsufficientStockException(productId, Math.abs(quantityChange), inventory.getAvailableQuantity());
        }

        // Validate we have available stock for decrements
        if (quantityChange < 0 && inventory.getAvailableQuantity() < Math.abs(quantityChange)) {
            throw new InsufficientStockException(productId, Math.abs(quantityChange), inventory.getAvailableQuantity());
        }

        inventory.setStockQuantity(newQuantity);
        inventory = inventoryRepository.saveAndFlush(inventory);

        // Record adjustment for audit
        recordAdjustment(inventory, productId, adjustment.adjustmentType(),
                quantityChange, quantityBefore, newQuantity,
                adjustment.reason(), adminId, adminUsername);

        // Check for low stock alert
        checkLowStock(inventory);

        log.info("Stock adjusted for product {}: {} -> {} ({})",
                productId, quantityBefore, newQuantity, adjustment.adjustmentType());

        return inventoryMapper.toDTO(inventory);
    }

    /**
     * Reserve stock for a pending order.
     * Reserved stock is still in inventory but not available for new orders.
     * Uses optimistic locking (@Version) - throws ObjectOptimisticLockingFailureException on conflict.
     */
    @Transactional
    public InventoryDTO reserveStock(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        InventoryEntity inventory = findInventoryOrThrow(productId);

        if (!inventory.hasAvailable(quantity)) {
            throw new InsufficientStockException(productId, quantity, inventory.getAvailableQuantity());
        }

        int previousReserved = inventory.getReservedQuantity();
        inventory.setReservedQuantity(previousReserved + quantity);
        inventory = inventoryRepository.saveAndFlush(inventory);

        log.info("Reserved {} units for product {}, total reserved: {}",
                quantity, productId, inventory.getReservedQuantity());

        return inventoryMapper.toDTO(inventory);
    }

    /**
     * Release previously reserved stock (e.g., order cancelled).
     * Uses optimistic locking (@Version) - throws ObjectOptimisticLockingFailureException on conflict.
     */
    @Transactional
    public InventoryDTO releaseReservedStock(UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        InventoryEntity inventory = findInventoryOrThrow(productId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new IllegalArgumentException(
                    String.format("Cannot release %d units, only %d reserved",
                            quantity, inventory.getReservedQuantity()));
        }

        int previousReserved = inventory.getReservedQuantity();
        inventory.setReservedQuantity(previousReserved - quantity);
        inventory = inventoryRepository.saveAndFlush(inventory);

        log.info("Released {} units for product {}, remaining reserved: {}",
                quantity, productId, inventory.getReservedQuantity());

        return inventoryMapper.toDTO(inventory);
    }

    /**
     * Commit reserved stock to a sale (order fulfilled).
     * Decreases both stock and reserved quantities.
     * Uses optimistic locking (@Version) - throws ObjectOptimisticLockingFailureException on conflict.
     */
    @Transactional
    public InventoryDTO commitReservedStock(UUID productId, int quantity,
                                            UUID adminId, String adminUsername) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        InventoryEntity inventory = findInventoryOrThrow(productId);

        if (inventory.getReservedQuantity() < quantity) {
            throw new IllegalArgumentException(
                    String.format("Cannot commit %d units, only %d reserved",
                            quantity, inventory.getReservedQuantity()));
        }

        int quantityBefore = inventory.getStockQuantity();
        inventory.setStockQuantity(quantityBefore - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventory = inventoryRepository.saveAndFlush(inventory);

        // Record adjustment for audit
        recordAdjustment(inventory, productId, "SALE",
                -quantity, quantityBefore, inventory.getStockQuantity(),
                "Order fulfilled", adminId, adminUsername);

        // Check for low stock alert
        checkLowStock(inventory);

        log.info("Committed {} units for product {}, stock: {} -> {}",
                quantity, productId, quantityBefore, inventory.getStockQuantity());

        return inventoryMapper.toDTO(inventory);
    }

    /**
     * Find products with low stock (below threshold).
     */
    @Transactional(readOnly = true)
    public List<InventoryDTO> findLowStockProducts(int threshold) {
        List<InventoryEntity> lowStock = inventoryRepository.findLowStock(threshold);
        return inventoryMapper.toDTOList(lowStock);
    }

    /**
     * Find products that are out of stock.
     */
    @Transactional(readOnly = true)
    public List<InventoryDTO> findOutOfStockProducts() {
        List<InventoryEntity> outOfStock = inventoryRepository.findOutOfStock();
        return inventoryMapper.toDTOList(outOfStock);
    }

    /**
     * Create inventory for a new product.
     */
    @Transactional
    public InventoryDTO createInventory(UUID productId, int initialStock) {
        if (inventoryRepository.existsByProductId(productId)) {
            throw new IllegalArgumentException("Inventory already exists for product: " + productId);
        }

        InventoryEntity inventory = new InventoryEntity(productId, initialStock);
        inventory = inventoryRepository.save(inventory);

        log.info("Created inventory for product {} with initial stock: {}", productId, initialStock);

        return inventoryMapper.toDTO(inventory);
    }

    /**
     * Get inventory for multiple products.
     */
    @Transactional(readOnly = true)
    public List<InventoryDTO> getInventoryBatch(List<UUID> productIds) {
        List<InventoryEntity> inventories = inventoryRepository.findByProductIdIn(productIds);
        return inventoryMapper.toDTOList(inventories);
    }

    // ===================== Private helpers =====================

    private InventoryEntity findInventoryOrThrow(UUID productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    private void recordAdjustment(InventoryEntity inventory, UUID productId,
                                  String adjustmentType, int quantityChange,
                                  int quantityBefore, int quantityAfter,
                                  String reason, UUID adminId, String adminUsername) {
        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity(
                inventory.getId(),
                productId,
                adjustmentType,
                quantityChange,
                quantityBefore,
                quantityAfter,
                reason,
                adminId,
                adminUsername
        );
        adjustmentRepository.save(adjustment);
    }

    private void checkLowStock(InventoryEntity inventory) {
        if (inventory.getStockQuantity() < LOW_STOCK_THRESHOLD) {
            eventPublisher.publishEvent(new LowStockEvent(
                    this,
                    inventory.getProductId(),
                    inventory.getStockQuantity(),
                    LOW_STOCK_THRESHOLD
            ));
        }
    }
}