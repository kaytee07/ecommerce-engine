package com.shop.ecommerceengine.cart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ecommerceengine.cart.dto.AddToCartDTO;
import com.shop.ecommerceengine.cart.dto.CartDTO;
import com.shop.ecommerceengine.cart.dto.CartItemDTO;
import com.shop.ecommerceengine.cart.entity.CartItem;
import com.shop.ecommerceengine.cart.exception.CartItemNotFoundException;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.exception.ProductNotFoundException;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import com.shop.ecommerceengine.inventory.exception.InsufficientStockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing guest (anonymous) carts in Redis.
 * Guest carts are stored only in Redis with TTL, not persisted to database.
 */
@Service
public class GuestCartService {

    private static final Logger log = LoggerFactory.getLogger(GuestCartService.class);
    private static final String GUEST_CART_PREFIX = "guest:cart:";
    private static final Duration GUEST_CART_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;
    private final InventoryServiceInterface inventoryService;
    private final ObjectMapper objectMapper;

    public GuestCartService(StringRedisTemplate redisTemplate,
                            ProductRepository productRepository,
                            InventoryServiceInterface inventoryService,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get cart for a guest session.
     */
    public CartDTO getCart(String sessionId) {
        List<CartItem> items = getCartItems(sessionId);
        return buildCartDTO(sessionId, items);
    }

    /**
     * Add item to guest cart.
     */
    public CartDTO addItem(String sessionId, AddToCartDTO request) {
        // Validate product exists and is active
        ProductEntity product = productRepository.findByIdAndActiveTrue(request.productId())
                .orElseThrow(() -> new ProductNotFoundException(request.productId()));

        List<CartItem> items = getCartItems(sessionId);

        // Calculate total quantity
        int existingQuantity = items.stream()
                .filter(i -> i.getProductId().equals(request.productId()))
                .mapToInt(CartItem::getQuantity)
                .sum();
        int totalQuantity = existingQuantity + request.quantity();

        // Check inventory
        if (!inventoryService.checkAvailability(request.productId(), totalQuantity)) {
            int available = inventoryService.getAvailableQuantity(request.productId());
            throw new InsufficientStockException(request.productId(), totalQuantity, available);
        }

        // Update or add item
        Optional<CartItem> existing = items.stream()
                .filter(i -> i.getProductId().equals(request.productId()))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(totalQuantity);
        } else {
            items.add(new CartItem(
                    product.getId(),
                    product.getName(),
                    request.quantity(),
                    product.getEffectivePrice()
            ));
        }

        saveCartItems(sessionId, items);
        log.info("Added {} x {} to guest cart {}", request.quantity(), product.getName(), sessionId);

        return buildCartDTO(sessionId, items);
    }

    /**
     * Update item quantity in guest cart.
     */
    public CartDTO updateItemQuantity(String sessionId, UUID productId, int quantity) {
        List<CartItem> items = getCartItems(sessionId);

        Optional<CartItem> existing = items.stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst();

        if (existing.isEmpty()) {
            throw new CartItemNotFoundException("Item not found in guest cart");
        }

        if (quantity <= 0) {
            items.remove(existing.get());
        } else {
            if (!inventoryService.checkAvailability(productId, quantity)) {
                int available = inventoryService.getAvailableQuantity(productId);
                throw new InsufficientStockException(productId, quantity, available);
            }
            existing.get().setQuantity(quantity);
        }

        saveCartItems(sessionId, items);
        return buildCartDTO(sessionId, items);
    }

    /**
     * Remove item from guest cart.
     */
    public CartDTO removeItem(String sessionId, UUID productId) {
        List<CartItem> items = getCartItems(sessionId);

        boolean removed = items.removeIf(i -> i.getProductId().equals(productId));
        if (!removed) {
            throw new CartItemNotFoundException("Item not found in guest cart");
        }

        saveCartItems(sessionId, items);
        log.info("Removed product {} from guest cart {}", productId, sessionId);

        return buildCartDTO(sessionId, items);
    }

    /**
     * Clear guest cart.
     */
    public CartDTO clearCart(String sessionId) {
        String key = GUEST_CART_PREFIX + sessionId;
        redisTemplate.delete(key);
        log.info("Cleared guest cart {}", sessionId);

        return buildCartDTO(sessionId, new ArrayList<>());
    }

    /**
     * Merge guest cart into user cart after login.
     */
    public void mergeIntoUserCart(String sessionId, UUID userId, CartService userCartService) {
        List<CartItem> guestItems = getCartItems(sessionId);

        for (CartItem item : guestItems) {
            try {
                userCartService.addItem(userId, new AddToCartDTO(item.getProductId(), item.getQuantity()));
            } catch (Exception e) {
                log.warn("Failed to merge guest cart item {} for user {}: {}",
                        item.getProductId(), userId, e.getMessage());
            }
        }

        // Clear guest cart after merge
        clearCart(sessionId);
        log.info("Merged guest cart {} into user cart {}", sessionId, userId);
    }

    // ===================== Private helpers =====================

    private List<CartItem> getCartItems(String sessionId) {
        String key = GUEST_CART_PREFIX + sessionId;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<CartItem>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing guest cart: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveCartItems(String sessionId, List<CartItem> items) {
        String key = GUEST_CART_PREFIX + sessionId;

        try {
            String json = objectMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(key, json, GUEST_CART_TTL);
        } catch (JsonProcessingException e) {
            log.error("Error saving guest cart: {}", e.getMessage());
        }
    }

    private CartDTO buildCartDTO(String sessionId, List<CartItem> items) {
        List<CartItemDTO> itemDTOs = items.stream()
                .map(item -> new CartItemDTO(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getPriceAtAdd(),
                        item.getSubtotal()
                ))
                .toList();

        int itemCount = items.stream().mapToInt(CartItem::getQuantity).sum();
        BigDecimal total = items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartDTO(
                null,
                null, // No userId for guest
                itemDTOs,
                itemCount,
                total,
                null
        );
    }
}
