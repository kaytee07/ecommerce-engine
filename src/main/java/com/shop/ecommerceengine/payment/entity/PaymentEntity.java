package com.shop.ecommerceengine.payment.entity;

import com.shop.ecommerceengine.common.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Payment entity representing a payment attempt for an order.
 * Supports soft delete and optimistic locking.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentGateway gateway;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "GHS";

    @Column(name = "checkout_url")
    private String checkoutUrl;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "gateway_response", columnDefinition = "text")
    private Map<String, Object> gatewayResponse;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refunded_by")
    private UUID refundedBy;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Default constructor for JPA
    protected PaymentEntity() {
    }

    /**
     * Create a new payment entity.
     */
    public PaymentEntity(UUID orderId, UUID userId, BigDecimal amount, String idempotencyKey, PaymentGateway gateway) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.gateway = gateway;
        this.status = PaymentStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ===================== Business Methods =====================

    /**
     * Mark payment as successful.
     */
    public void markSuccess(String transactionRef, Map<String, Object> gatewayResponse) {
        if (!status.canTransitionTo(PaymentStatus.SUCCESS)) {
            throw new IllegalStateException("Cannot mark payment as success from status: " + status);
        }
        this.status = PaymentStatus.SUCCESS;
        this.transactionRef = transactionRef;
        this.gatewayResponse = gatewayResponse;
    }

    /**
     * Mark payment as failed.
     */
    public void markFailed(String reason, Map<String, Object> gatewayResponse) {
        if (!status.canTransitionTo(PaymentStatus.FAILED)) {
            throw new IllegalStateException("Cannot mark payment as failed from status: " + status);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.gatewayResponse = gatewayResponse;
    }

    /**
     * Mark payment as refunded.
     */
    public void markRefunded(UUID refundedBy, String reason) {
        if (!status.canTransitionTo(PaymentStatus.REFUNDED)) {
            throw new IllegalStateException("Cannot refund payment from status: " + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.refundedBy = refundedBy;
        this.refundReason = reason;
        this.refundedAt = Instant.now();
    }

    /**
     * Mark payment as cancelled.
     */
    public void markCancelled(String reason) {
        if (!status.canTransitionTo(PaymentStatus.CANCELLED)) {
            throw new IllegalStateException("Cannot cancel payment from status: " + status);
        }
        this.status = PaymentStatus.CANCELLED;
        this.failureReason = reason;
    }

    /**
     * Soft delete the payment.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * Check if payment is deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // ===================== Getters and Setters =====================

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public PaymentGateway getGateway() {
        return gateway;
    }

    public void setGateway(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public Map<String, Object> getGatewayResponse() {
        return gatewayResponse;
    }

    public void setGatewayResponse(Map<String, Object> gatewayResponse) {
        this.gatewayResponse = gatewayResponse;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public UUID getRefundedBy() {
        return refundedBy;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
