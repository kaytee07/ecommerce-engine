package com.shop.ecommerceengine.cart.service;

import com.shop.ecommerceengine.cart.dto.CartDTO;
import com.shop.ecommerceengine.cart.dto.CartItemDTO;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import com.shop.ecommerceengine.order.service.CartServiceInterface;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Adapter that implements CartServiceInterface for cross-module communication.
 * Bridges the Cart module to the Order module without tight coupling.
 */
@Service
public class CartServiceAdapter implements CartServiceInterface {

    private final CartService cartService;
    private final ProductRepository productRepository;

    public CartServiceAdapter(CartService cartService, ProductRepository productRepository) {
        this.cartService = cartService;
        this.productRepository = productRepository;
    }

    @Override
    public List<CartItemSnapshot> getCartItems(UUID userId) {
        CartDTO cart = cartService.getCart(userId);
        return cart.items().stream()
                .map(item -> {
                    // Get SKU from product repository
                    String sku = productRepository.findById(item.productId())
                            .map(ProductEntity::getSku)
                            .orElse(null);
                    return new CartItemSnapshot(
                            item.productId(),
                            item.productName(),
                            sku,
                            item.quantity(),
                            item.priceAtAdd()
                    );
                })
                .toList();
    }

    @Override
    public boolean isCartEmpty(UUID userId) {
        CartDTO cart = cartService.getCart(userId);
        return cart.items().isEmpty();
    }

    @Override
    @CacheEvict(value = "carts", key = "'cart:' + #userId", beforeInvocation = true)
    public void clearCart(UUID userId) {
        cartService.clearCart(userId);
    }

    @Override
    public BigDecimal getCartTotal(UUID userId) {
        CartDTO cart = cartService.getCart(userId);
        return cart.totalAmount();
    }
}
