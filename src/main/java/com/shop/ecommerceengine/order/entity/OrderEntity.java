package com.shop.ecommerceengine.order.entity;

import com.shop.ecommerceengine.order.converter.OrderItemListConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a customer order.
 * Uses @Version for optimistic locking and JSONB for items.
 * Supports soft deletes via deletedAt field.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "items", columnDefinition = "text")
    @Convert(converter = OrderItemListConverter.class)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "notes")
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public OrderEntity() {
    }

    public OrderEntity(UUID userId) {
        this.userId = userId;
        this.status = OrderStatus.PENDING;
        this.items = new ArrayList<>();
        this.totalAmount = BigDecimal.ZERO;
    }

    /**
     * Calculate total amount from all items.
     */
    public BigDecimal calculateTotalAmount() {
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total number of items (sum of quantities).
     */
    public int getItemCount() {
        return items.stream()
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /**
     * Add item to order and recalculate total.
     */
    public void addItem(OrderItem item) {
        List<OrderItem> newItems = new ArrayList<>(items);
        newItems.add(item);
        this.items = newItems;
        this.totalAmount = calculateTotalAmount();
    }

    /**
     * Set all items and recalculate total.
     */
    public void setItemsAndRecalculate(List<OrderItem> newItems) {
        this.items = new ArrayList<>(newItems);
        this.totalAmount = calculateTotalAmount();
    }

    /**
     * Check if order can transition to target status.
     */
    public boolean canTransitionTo(OrderStatus target) {
        return status.canTransitionTo(target);
    }

    /**
     * Transition to new status if valid.
     *
     * @param newStatus target status
     * @return true if transition was successful
     */
    public boolean transitionTo(OrderStatus newStatus) {
        if (canTransitionTo(newStatus)) {
            this.status = newStatus;
            return true;
        }
        return false;
    }

    /**
     * Check if order is soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete the order.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
