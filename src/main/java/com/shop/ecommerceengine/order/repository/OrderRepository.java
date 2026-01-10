package com.shop.ecommerceengine.order.repository;

import com.shop.ecommerceengine.order.entity.OrderEntity;
import com.shop.ecommerceengine.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Repository for OrderEntity with custom queries supporting soft deletes.
 */
@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    // ==================== User Queries (exclude soft deleted) ====================

    /**
     * Find order by ID if not soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<OrderEntity> findByIdAndNotDeleted(@Param("id") UUID id);

    /**
     * Find order by ID and user ID if not soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id AND o.userId = :userId AND o.deletedAt IS NULL")
    Optional<OrderEntity> findByIdAndUserIdAndNotDeleted(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Find all orders for a user, ordered by creation date descending (newest first).
     * Excludes soft deleted orders.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    /**
     * Find orders for a user with pagination, excluding soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.deletedAt IS NULL")
    Page<OrderEntity> findByUserIdAndNotDeleted(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find orders for a user by status, excluding soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.status = :status AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    List<OrderEntity> findByUserIdAndStatusAndNotDeleted(@Param("userId") UUID userId, @Param("status") OrderStatus status);

    // ==================== Admin Queries (exclude soft deleted) ====================

    /**
     * Find all orders not soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    List<OrderEntity> findAllNotDeleted();

    /**
     * Find all orders with pagination, excluding soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.deletedAt IS NULL")
    Page<OrderEntity> findAllNotDeleted(Pageable pageable);

    /**
     * Find orders by status, excluding soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    List<OrderEntity> findByStatusAndNotDeleted(@Param("status") OrderStatus status);

    /**
     * Find orders by status with pagination, excluding soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.deletedAt IS NULL")
    Page<OrderEntity> findByStatusAndNotDeleted(@Param("status") OrderStatus status, Pageable pageable);

    /**
     * Find pending orders (for fulfillment), excluding soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.status = 'PENDING' AND o.deletedAt IS NULL ORDER BY o.createdAt ASC")
    List<OrderEntity> findPendingOrders();

    /**
     * Find orders awaiting fulfillment (CONFIRMED or PROCESSING), excluding soft deleted.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.status IN ('CONFIRMED', 'PROCESSING') AND o.deletedAt IS NULL ORDER BY o.createdAt ASC")
    List<OrderEntity> findOrdersAwaitingFulfillment();

    // ==================== Count Queries ====================

    /**
     * Count orders by status, excluding soft deleted.
     */
    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.status = :status AND o.deletedAt IS NULL")
    long countByStatusAndNotDeleted(@Param("status") OrderStatus status);

    /**
     * Count all orders for a user, excluding soft deleted.
     */
    @Query("SELECT COUNT(o) FROM OrderEntity o WHERE o.userId = :userId AND o.deletedAt IS NULL")
    long countByUserIdAndNotDeleted(@Param("userId") UUID userId);

    // ==================== Soft Delete Operations ====================

    /**
     * Soft delete an order by setting deletedAt timestamp.
     */
    @Modifying
    @Query("UPDATE OrderEntity o SET o.deletedAt = :deletedAt WHERE o.id = :id")
    int softDelete(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);

    /**
     * Check if order exists and is not soft deleted.
     */
    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM OrderEntity o WHERE o.id = :id AND o.deletedAt IS NULL")
    boolean existsByIdAndNotDeleted(@Param("id") UUID id);

    // ==================== Streaming Queries (for exports) ====================

    /**
     * Stream orders created between dates for export.
     * Must be used within a transaction.
     */
    @Query("SELECT o FROM OrderEntity o WHERE o.createdAt BETWEEN :from AND :to AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    Stream<OrderEntity> streamByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
