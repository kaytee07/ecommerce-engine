package com.shop.ecommerceengine.cart.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents an item in the cart.
 * Stored as JSONB in the database.
 */
public class CartItem implements Serializable {

    private UUID productId;
    private String productName;
    private int quantity;
    private BigDecimal priceAtAdd;

    public CartItem() {
    }

    public CartItem(UUID productId, String productName, int quantity, BigDecimal priceAtAdd) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.priceAtAdd = priceAtAdd;
    }

    /**
     * Calculate subtotal for this item.
     * Excluded from JSON serialization since it's computed.
     */
    @JsonIgnore
    public BigDecimal getSubtotal() {
        return priceAtAdd.multiply(BigDecimal.valueOf(quantity));
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPriceAtAdd() {
        return priceAtAdd;
    }

    public void setPriceAtAdd(BigDecimal priceAtAdd) {
        this.priceAtAdd = priceAtAdd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CartItem cartItem = (CartItem) o;
        return productId != null && productId.equals(cartItem.productId);
    }

    @Override
    public int hashCode() {
        return productId != null ? productId.hashCode() : 0;
    }
}
