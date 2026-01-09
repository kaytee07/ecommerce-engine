package com.shop.ecommerceengine.inventory.repository;

import com.shop.ecommerceengine.inventory.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for inventory operations.
 */
@Repository
public interface InventoryRepository extends JpaRepository<InventoryEntity, UUID> {

    /**
     * Find inventory by product ID.
     */
    Optional<InventoryEntity> findByProductId(UUID productId);

    /**
     * Check if inventory exists for product.
     */
    boolean existsByProductId(UUID productId);

    /**
     * Find products with low stock (below threshold).
     */
    @Query("SELECT i FROM InventoryEntity i WHERE i.stockQuantity < :threshold")
    List<InventoryEntity> findLowStock(@Param("threshold") int threshold);

    /**
     * Find products with available quantity below threshold.
     */
    @Query("SELECT i FROM InventoryEntity i WHERE (i.stockQuantity - i.reservedQuantity) < :threshold")
    List<InventoryEntity> findLowAvailableStock(@Param("threshold") int threshold);

    /**
     * Find products that are out of stock.
     */
    @Query("SELECT i FROM InventoryEntity i WHERE i.stockQuantity = 0 OR (i.stockQuantity - i.reservedQuantity) <= 0")
    List<InventoryEntity> findOutOfStock();

    /**
     * Find inventory for multiple products.
     */
    List<InventoryEntity> findByProductIdIn(List<UUID> productIds);
}
