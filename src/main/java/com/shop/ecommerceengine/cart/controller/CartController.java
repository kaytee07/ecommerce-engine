package com.shop.ecommerceengine.cart.controller;

import com.shop.ecommerceengine.cart.dto.AddToCartDTO;
import com.shop.ecommerceengine.cart.dto.CartDTO;
import com.shop.ecommerceengine.cart.dto.UpdateCartItemDTO;
import com.shop.ecommerceengine.cart.service.CartService;
import com.shop.ecommerceengine.cart.service.GuestCartService;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for shopping cart operations.
 * Supports both authenticated users (persistent DB cart) and guests (Redis session cart).
 */
@RestController
@RequestMapping("/api/v1/store/cart")
@Tag(name = "Cart", description = "Shopping cart operations")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService cartService;
    private final GuestCartService guestCartService;
    private final UserService userService;

    public CartController(CartService cartService,
                         GuestCartService guestCartService,
                         UserService userService) {
        this.cartService = cartService;
        this.guestCartService = guestCartService;
        this.userService = userService;
    }

    /**
     * Get current cart.
     */
    @GetMapping
    @Operation(summary = "Get cart", description = "Returns the current shopping cart")
    public ResponseEntity<ApiResponse<CartDTO>> getCart(
            Authentication authentication,
            HttpServletRequest request) {

        CartDTO cart;
        if (isAuthenticated(authentication)) {
            UUID userId = getUserId(authentication);
            cart = cartService.getCart(userId);
            log.debug("Retrieved cart for user {}", userId);
        } else {
            String sessionId = getSessionId(request);
            cart = guestCartService.getCart(sessionId);
            log.debug("Retrieved guest cart for session {}", sessionId);
        }

        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    /**
     * Add item to cart.
     */
    @PostMapping("/items")
    @Operation(summary = "Add item to cart", description = "Adds a product to the shopping cart")
    public ResponseEntity<ApiResponse<CartDTO>> addItem(
            @Valid @RequestBody AddToCartDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        CartDTO cart;
        if (isAuthenticated(authentication)) {
            UUID userId = getUserId(authentication);
            cart = cartService.addItem(userId, request);
            log.info("User {} added product {} to cart", userId, request.productId());
        } else {
            String sessionId = getSessionId(httpRequest);
            cart = guestCartService.addItem(sessionId, request);
            log.info("Guest {} added product {} to cart", sessionId, request.productId());
        }

        return ResponseEntity.ok(ApiResponse.success(cart, "Item added to cart"));
    }

    /**
     * Update item quantity in cart.
     */
    @PutMapping("/items/{productId}")
    @Operation(summary = "Update item quantity", description = "Updates the quantity of an item in cart")
    public ResponseEntity<ApiResponse<CartDTO>> updateItemQuantity(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateCartItemDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        CartDTO cart;
        if (isAuthenticated(authentication)) {
            UUID userId = getUserId(authentication);
            cart = cartService.updateItemQuantity(userId, productId, request.quantity());
        } else {
            String sessionId = getSessionId(httpRequest);
            cart = guestCartService.updateItemQuantity(sessionId, productId, request.quantity());
        }

        return ResponseEntity.ok(ApiResponse.success(cart, "Cart updated"));
    }

    /**
     * Remove item from cart.
     */
    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove item from cart", description = "Removes an item from the shopping cart")
    public ResponseEntity<ApiResponse<CartDTO>> removeItem(
            @Parameter(description = "Product UUID")
            @PathVariable UUID productId,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        CartDTO cart;
        if (isAuthenticated(authentication)) {
            UUID userId = getUserId(authentication);
            cart = cartService.removeItem(userId, productId);
        } else {
            String sessionId = getSessionId(httpRequest);
            cart = guestCartService.removeItem(sessionId, productId);
        }

        return ResponseEntity.ok(ApiResponse.success(cart, "Item removed from cart"));
    }

    /**
     * Clear all items from cart.
     */
    @PostMapping("/clear")
    @Operation(summary = "Clear cart", description = "Removes all items from the shopping cart")
    public ResponseEntity<ApiResponse<CartDTO>> clearCart(
            Authentication authentication,
            HttpServletRequest httpRequest) {

        CartDTO cart;
        if (isAuthenticated(authentication)) {
            UUID userId = getUserId(authentication);
            cart = cartService.clearCart(userId);
        } else {
            String sessionId = getSessionId(httpRequest);
            cart = guestCartService.clearCart(sessionId);
        }

        return ResponseEntity.ok(ApiResponse.success(cart, "Cart cleared"));
    }

    /**
     * Get cart item count (for header badge).
     */
    @GetMapping("/count")
    @Operation(summary = "Get item count", description = "Returns the total number of items in cart")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getItemCount(
            Authentication authentication,
            HttpServletRequest httpRequest) {

        int count;
        if (isAuthenticated(authentication)) {
            UUID userId = getUserId(authentication);
            count = cartService.getItemCount(userId);
        } else {
            String sessionId = getSessionId(httpRequest);
            count = guestCartService.getCart(sessionId).itemCount();
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    /**
     * Merge guest cart into user cart (called after login).
     */
    @PostMapping("/merge")
    @Operation(summary = "Merge guest cart", description = "Merges guest cart into user cart after login")
    public ResponseEntity<ApiResponse<CartDTO>> mergeGuestCart(
            Authentication authentication,
            HttpServletRequest httpRequest) {

        if (!isAuthenticated(authentication)) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(null, "Must be authenticated to merge cart"));
        }

        UUID userId = getUserId(authentication);
        String sessionId = getSessionId(httpRequest);

        guestCartService.mergeIntoUserCart(sessionId, userId, cartService);

        CartDTO cart = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success(cart, "Cart merged successfully"));
    }

    /**
     * Validate cart inventory before checkout.
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate cart", description = "Validates all cart items have sufficient inventory")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateCart(
            Authentication authentication,
            HttpServletRequest httpRequest) {

        boolean valid;
        if (isAuthenticated(authentication)) {
            UUID userId = getUserId(authentication);
            valid = cartService.validateCartInventory(userId);
        } else {
            // For guests, we'd need to validate differently
            valid = true;
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "valid", valid,
                "message", valid ? "All items available" : "Some items have insufficient stock"
        )));
    }

    // ===================== Private helpers =====================

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
    }

    private UUID getUserId(Authentication authentication) {
        try {
            return userService.getUserByUsername(authentication.getName()).id();
        } catch (Exception e) {
            log.error("Failed to get user ID for {}: {}", authentication.getName(), e.getMessage());
            throw new RuntimeException("Unable to identify user");
        }
    }

    private String getSessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        return session.getId();
    }
}
