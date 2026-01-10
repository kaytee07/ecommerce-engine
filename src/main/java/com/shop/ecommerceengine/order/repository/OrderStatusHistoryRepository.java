package com.shop.ecommerceengine.order.repository;

import com.shop.ecommerceengine.order.entity.OrderStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for order status history (audit trail).
 */
@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistoryEntity, UUID> {

    /**
     * Find all status changes for an order, ordered by creation date.
     */
    List<OrderStatusHistoryEntity> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    /**
     * Find the most recent status change for an order.
     */
    OrderStatusHistoryEntity findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
