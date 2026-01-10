package com.shop.ecommerceengine.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents an item in an order.
 * Stored as JSONB in the database.
 * Captures product details at order time for historical accuracy.
 */
public class OrderItem implements Serializable {

    private UUID productId;
    private String productName;
    private String sku;
    private int quantity;
    private BigDecimal priceAtOrder;

    public OrderItem() {
    }

    public OrderItem(UUID productId, String productName, String sku, int quantity, BigDecimal priceAtOrder) {
        this.productId = productId;
        this.productName = productName;
        this.sku = sku;
        this.quantity = quantity;
        this.priceAtOrder = priceAtOrder;
    }

    /**
     * Calculate subtotal for this item.
     * Excluded from JSON serialization since it's computed.
     */
    @JsonIgnore
    public BigDecimal getSubtotal() {
        return priceAtOrder.multiply(BigDecimal.valueOf(quantity));
    }

    // Getters and Setters

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPriceAtOrder() {
        return priceAtOrder;
    }

    public void setPriceAtOrder(BigDecimal priceAtOrder) {
        this.priceAtOrder = priceAtOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return productId != null && productId.equals(orderItem.productId);
    }

    @Override
    public int hashCode() {
        return productId != null ? productId.hashCode() : 0;
    }
}
