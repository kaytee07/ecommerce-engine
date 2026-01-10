package com.shop.ecommerceengine.cart.repository;

import com.shop.ecommerceengine.cart.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for cart operations.
 */
@Repository
public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    /**
     * Find cart by user ID.
     */
    Optional<CartEntity> findByUserId(UUID userId);

    /**
     * Check if user has a cart.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Delete cart by user ID.
     */
    void deleteByUserId(UUID userId);

    /**
     * Delete carts not updated since a given time (for cleanup).
     */
    @Modifying
    @Query("DELETE FROM CartEntity c WHERE c.updatedAt < :cutoff")
    int deleteCartsNotUpdatedSince(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Count carts with items (for analytics).
     */
    @Query("SELECT COUNT(c) FROM CartEntity c WHERE SIZE(c.items) > 0")
    long countCartsWithItems();
}
