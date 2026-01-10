package com.shop.ecommerceengine.cart.entity;

import com.shop.ecommerceengine.cart.converter.CartItemListConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entity representing a shopping cart for logged-in users.
 * Uses @Version for optimistic locking and JSONB for items.
 */
@Entity
@Table(name = "carts")
public class CartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "items", columnDefinition = "text")
    @Convert(converter = CartItemListConverter.class)
    private List<CartItem> items = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CartEntity() {
    }

    public CartEntity(UUID userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
    }

    /**
     * Calculate total amount from all items.
     */
    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total number of items (sum of quantities).
     */
    public int getItemCount() {
        return items.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();
    }

    /**
     * Find item by product ID.
     */
    public Optional<CartItem> findItemByProductId(UUID productId) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();
    }

    /**
     * Add or update item in cart.
     * Creates a new list to ensure JPA dirty detection works with the converter.
     */
    public void addOrUpdateItem(CartItem item) {
        List<CartItem> newItems = new ArrayList<>(items);
        Optional<CartItem> existing = newItems.stream()
                .filter(i -> i.getProductId().equals(item.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + item.getQuantity());
        } else {
            newItems.add(item);
        }
        this.items = newItems;
    }

    /**
     * Update quantity of an existing item.
     * Creates a new list to ensure JPA dirty detection works with the converter.
     */
    public boolean updateItemQuantity(UUID productId, int quantity) {
        Optional<CartItem> existing = findItemByProductId(productId);
        if (existing.isEmpty()) {
            return false;
        }

        List<CartItem> newItems = new ArrayList<>();
        for (CartItem item : items) {
            if (item.getProductId().equals(productId)) {
                if (quantity > 0) {
                    CartItem updated = new CartItem(
                            item.getProductId(),
                            item.getProductName(),
                            quantity,
                            item.getPriceAtAdd()
                    );
                    newItems.add(updated);
                }
                // If quantity <= 0, we skip adding (removes the item)
            } else {
                newItems.add(item);
            }
        }
        this.items = newItems;
        return true;
    }

    /**
     * Remove item from cart.
     * Creates a new list to ensure JPA dirty detection works with the converter.
     */
    public boolean removeItem(UUID productId) {
        boolean found = items.stream().anyMatch(item -> item.getProductId().equals(productId));
        if (found) {
            List<CartItem> newItems = items.stream()
                    .filter(item -> !item.getProductId().equals(productId))
                    .collect(java.util.stream.Collectors.toList());
            this.items = newItems;
            return true;
        }
        return false;
    }

    /**
     * Clear all items from cart.
     */
    public void clearItems() {
        this.items = new ArrayList<>();
    }

    /**
     * Get current quantity of a product in cart.
     */
    public int getQuantityForProduct(UUID productId) {
        return findItemByProductId(productId)
                .map(CartItem::getQuantity)
                .orElse(0);
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

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items != null ? items : new ArrayList<>();
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
}
