package com.shop.ecommerceengine.inventory.repository;

import com.shop.ecommerceengine.inventory.entity.InventoryAdjustmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for inventory adjustment audit records.
 */
@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustmentEntity, UUID> {

    /**
     * Find adjustments by product ID.
     */
    Page<InventoryAdjustmentEntity> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    /**
     * Find adjustments by inventory ID.
     */
    List<InventoryAdjustmentEntity> findByInventoryIdOrderByCreatedAtDesc(UUID inventoryId);

    /**
     * Find adjustments in a date range.
     */
    Page<InventoryAdjustmentEntity> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find adjustments by type.
     */
    Page<InventoryAdjustmentEntity> findByAdjustmentTypeOrderByCreatedAtDesc(String adjustmentType, Pageable pageable);

    /**
     * Find adjustments by admin.
     */
    Page<InventoryAdjustmentEntity> findByAdminIdOrderByCreatedAtDesc(UUID adminId, Pageable pageable);
}
