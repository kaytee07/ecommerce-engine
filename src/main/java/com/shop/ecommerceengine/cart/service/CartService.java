package com.shop.ecommerceengine.cart.service;

import com.shop.ecommerceengine.cart.dto.AddToCartDTO;
import com.shop.ecommerceengine.cart.dto.CartDTO;
import com.shop.ecommerceengine.cart.entity.CartEntity;
import com.shop.ecommerceengine.cart.entity.CartItem;
import com.shop.ecommerceengine.cart.exception.CartItemNotFoundException;
import com.shop.ecommerceengine.cart.mapper.CartMapper;
import com.shop.ecommerceengine.cart.repository.CartRepository;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.exception.ProductNotFoundException;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import com.shop.ecommerceengine.inventory.exception.InsufficientStockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for shopping cart operations.
 * Integrates with inventory for stock validation and uses Redis caching.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final String CACHE_NAME = "carts";

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryServiceInterface inventoryService;
    private final CartMapper cartMapper;

    public CartService(CartRepository cartRepository,
                       ProductRepository productRepository,
                       InventoryServiceInterface inventoryService,
                       CartMapper cartMapper) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.cartMapper = cartMapper;
    }

    /**
     * Get cart for a user.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CACHE_NAME, key = "'cart:' + #userId")
    public CartDTO getCart(UUID userId) {
        return cartRepository.findByUserId(userId)
                .map(cartMapper::toDTO)
                .orElse(CartDTO.empty(userId));
    }

    /**
     * Add item to cart with inventory validation.
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'cart:' + #userId")
    public CartDTO addItem(UUID userId, AddToCartDTO request) {
        // Validate product exists and is active
        ProductEntity product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        // Get or create cart
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> new CartEntity(userId));

        // Calculate total quantity (existing + new)
        int existingQuantity = cart.getQuantityForProduct(request.productId());
        int totalQuantity = existingQuantity + request.quantity();

        // Check inventory availability for total quantity
        if (!inventoryService.checkAvailability(request.productId(), totalQuantity)) {
            int available = inventoryService.getAvailableQuantity(request.productId());
            throw new InsufficientStockException(request.productId(), totalQuantity, available);
        }

        // Create and add cart item
        CartItem item = new CartItem(
                product.getId(),
                product.getName(),
                request.quantity(),
                product.getEffectivePrice()
        );
        cart.addOrUpdateItem(item);

        cart = cartRepository.save(cart);

        log.info("Added {} x {} to cart for user {}", request.quantity(), product.getName(), userId);

        return cartMapper.toDTO(cart);
    }

    /**
     * Update item quantity in cart.
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'cart:' + #userId")
    public CartDTO updateItemQuantity(UUID userId, UUID productId, int quantity) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartItemNotFoundException(userId, productId));

        if (cart.findItemByProductId(productId).isEmpty()) {
            throw new CartItemNotFoundException(userId, productId);
        }

        // If quantity > 0, validate inventory
        if (quantity > 0) {
            if (!inventoryService.checkAvailability(productId, quantity)) {
                int available = inventoryService.getAvailableQuantity(productId);
                throw new InsufficientStockException(productId, quantity, available);
            }
        }

        // Update or remove item
        cart.updateItemQuantity(productId, quantity);
        cart = cartRepository.save(cart);

        log.info("Updated quantity for product {} to {} in cart for user {}", productId, quantity, userId);

        return cartMapper.toDTO(cart);
    }

    /**
     * Remove item from cart.
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'cart:' + #userId")
    public CartDTO removeItem(UUID userId, UUID productId) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartItemNotFoundException(userId, productId));

        if (!cart.removeItem(productId)) {
            throw new CartItemNotFoundException(userId, productId);
        }

        cart = cartRepository.save(cart);

        log.info("Removed product {} from cart for user {}", productId, userId);

        return cartMapper.toDTO(cart);
    }

    /**
     * Clear all items from cart.
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'cart:' + #userId")
    public CartDTO clearCart(UUID userId) {
        CartEntity cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> new CartEntity(userId));

        cart.clearItems();
        cart = cartRepository.save(cart);

        log.info("Cleared cart for user {}", userId);

        return cartMapper.toDTO(cart);
    }

    /**
     * Delete cart entirely.
     */
    @Transactional
    @CacheEvict(value = CACHE_NAME, key = "'cart:' + #userId")
    public void deleteCart(UUID userId) {
        cartRepository.deleteByUserId(userId);
        log.info("Deleted cart for user {}", userId);
    }

    /**
     * Get cart item count for a user (for header display).
     */
    @Transactional(readOnly = true)
    public int getItemCount(UUID userId) {
        return cartRepository.findByUserId(userId)
                .map(CartEntity::getItemCount)
                .orElse(0);
    }

    /**
     * Validate all items in cart still have inventory available.
     * Used before checkout.
     */
    @Transactional(readOnly = true)
    public boolean validateCartInventory(UUID userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> cart.getItems().stream()
                        .allMatch(item -> inventoryService.checkAvailability(
                                item.getProductId(),
                                item.getQuantity()
                        )))
                .orElse(true);
    }
}
