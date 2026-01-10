package com.shop.ecommerceengine.payment.repository;

import com.shop.ecommerceengine.payment.entity.PaymentEntity;
import com.shop.ecommerceengine.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Repository for payment entities.
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    /**
     * Find payment by ID (not deleted).
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<PaymentEntity> findByIdAndNotDeleted(@Param("id") UUID id);

    /**
     * Find payment by idempotency key.
     */
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find payment by transaction reference.
     */
    Optional<PaymentEntity> findByTransactionRef(String transactionRef);

    /**
     * Find payments by order ID (not deleted).
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.orderId = :orderId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<PaymentEntity> findByOrderIdAndNotDeleted(@Param("orderId") UUID orderId);

    /**
     * Find payments by user ID (not deleted).
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.userId = :userId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<PaymentEntity> findByUserIdAndNotDeleted(@Param("userId") UUID userId);

    /**
     * Find payments by status (not deleted).
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.status = :status AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<PaymentEntity> findByStatusAndNotDeleted(@Param("status") PaymentStatus status);

    /**
     * Find pending payments older than threshold.
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.status = 'PENDING' AND p.createdAt < :threshold AND p.deletedAt IS NULL")
    List<PaymentEntity> findStalePendingPayments(@Param("threshold") Instant threshold);

    /**
     * Find all payments (not deleted).
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<PaymentEntity> findAllNotDeleted();

    /**
     * Soft delete a payment.
     */
    @Modifying
    @Query("UPDATE PaymentEntity p SET p.deletedAt = :deletedAt WHERE p.id = :id")
    int softDelete(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);

    /**
     * Check if idempotency key exists.
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Find successful payment for an order.
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.orderId = :orderId AND p.status = 'SUCCESS' AND p.deletedAt IS NULL")
    Optional<PaymentEntity> findSuccessfulPaymentByOrderId(@Param("orderId") UUID orderId);

    /**
     * Stream payments created between dates for export.
     * Must be used within a transaction.
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.createdAt BETWEEN :from AND :to AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    Stream<PaymentEntity> streamByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);
}
