package com.shop.ecommerceengine.cart;

import com.shop.ecommerceengine.cart.dto.AddToCartDTO;
import com.shop.ecommerceengine.cart.dto.CartDTO;
import com.shop.ecommerceengine.cart.entity.CartEntity;
import com.shop.ecommerceengine.cart.exception.CartItemNotFoundException;
import com.shop.ecommerceengine.cart.repository.CartRepository;
import com.shop.ecommerceengine.cart.service.CartService;
import com.shop.ecommerceengine.cart.service.InventoryServiceInterface;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import com.shop.ecommerceengine.inventory.exception.InsufficientStockException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CartService.
 * Uses test implementation of InventoryServiceInterface for cross-module communication.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CartServiceIntegrationTest {

    private static RedisServer redisServer;

    /**
     * Test configuration that provides a controllable InventoryServiceInterface.
     */
    @TestConfiguration
    static class TestInventoryConfig {
        @Bean
        @Primary
        public InventoryServiceInterface testInventoryService() {
            return new TestInventoryService();
        }
    }

    /**
     * Test implementation of InventoryServiceInterface with controllable behavior.
     */
    static class TestInventoryService implements InventoryServiceInterface {
        private final ConcurrentMap<UUID, Integer> availability = new ConcurrentHashMap<>();
        private boolean defaultAvailable = true;
        private int defaultQuantity = 100;

        @Override
        public boolean checkAvailability(UUID productId, int quantity) {
            int available = availability.getOrDefault(productId, defaultQuantity);
            return available >= quantity;
        }

        @Override
        public int getAvailableQuantity(UUID productId) {
            return availability.getOrDefault(productId, defaultQuantity);
        }

        public void setAvailability(UUID productId, int quantity) {
            availability.put(productId, quantity);
        }

        public void setDefaultQuantity(int quantity) {
            this.defaultQuantity = quantity;
        }

        public void reset() {
            availability.clear();
            defaultAvailable = true;
            defaultQuantity = 100;
        }
    }

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryServiceInterface inventoryService;

    private UUID testUserId;
    private UUID testProductId;
    private UUID testProductId2;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6377);
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:carttest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.data.redis.port", () -> 6377);
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();

        // Reset test inventory service
        if (inventoryService instanceof TestInventoryService testInv) {
            testInv.reset();
        }

        // Create test products
        ProductEntity product1 = new ProductEntity();
        product1.setName("Test Product 1");
        product1.setSlug("test-product-cart-1-" + System.currentTimeMillis());
        product1.setPrice(new BigDecimal("49.99"));
        product1.setSku("CART-TEST-1-" + System.currentTimeMillis());
        product1.setActive(true);
        product1 = productRepository.save(product1);
        testProductId = product1.getId();

        ProductEntity product2 = new ProductEntity();
        product2.setName("Test Product 2");
        product2.setSlug("test-product-cart-2-" + System.currentTimeMillis());
        product2.setPrice(new BigDecimal("29.99"));
        product2.setSku("CART-TEST-2-" + System.currentTimeMillis());
        product2.setActive(true);
        product2 = productRepository.save(product2);
        testProductId2 = product2.getId();
    }

    @AfterEach
    void tearDown() {
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ==================== Get Cart Tests ====================

    @Test
    @Order(1)
    @DisplayName("getCart returns empty cart for new user")
    void getCart_newUser_returnsEmptyCart() {
        CartDTO cart = cartService.getCart(testUserId);

        assertThat(cart).isNotNull();
        assertThat(cart.userId()).isEqualTo(testUserId);
        assertThat(cart.items()).isEmpty();
        assertThat(cart.totalAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(cart.itemCount()).isEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("getCart returns existing cart with items")
    void getCart_existingCart_returnsCartWithItems() {
        // Add item first
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));

        CartDTO cart = cartService.getCart(testUserId);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).productId()).isEqualTo(testProductId);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
    }

    // ==================== Add Item Tests ====================

    @Test
    @Order(10)
    @DisplayName("addItem adds new item to cart")
    void addItem_newItem_addsToCart() {
        CartDTO cart = cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).productId()).isEqualTo(testProductId);
        assertThat(cart.items().get(0).quantity()).isEqualTo(2);
        assertThat(cart.items().get(0).priceAtAdd()).isEqualByComparingTo(new BigDecimal("49.99"));
    }

    @Test
    @Order(11)
    @DisplayName("addItem increases quantity for existing item")
    void addItem_existingItem_increasesQuantity() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        CartDTO cart = cartService.addItem(testUserId, new AddToCartDTO(testProductId, 3));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    @Order(12)
    @DisplayName("addItem adds multiple different products")
    void addItem_multipleProducts_addsBoth() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        CartDTO cart = cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 1));

        assertThat(cart.items()).hasSize(2);
        assertThat(cart.itemCount()).isEqualTo(3);
    }

    @Test
    @Order(13)
    @DisplayName("addItem calculates total correctly")
    void addItem_calculatesTotal_correctly() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));  // 2 * 49.99 = 99.98
        CartDTO cart = cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 1));  // 1 * 29.99 = 29.99

        // Total: 99.98 + 29.99 = 129.97
        assertThat(cart.totalAmount()).isEqualByComparingTo(new BigDecimal("129.97"));
    }

    @Test
    @Order(14)
    @DisplayName("addItem throws InsufficientStockException when inventory unavailable")
    void addItem_insufficientStock_throwsException() {
        if (inventoryService instanceof TestInventoryService testInv) {
            testInv.setAvailability(testProductId, 5);
        }

        assertThatThrownBy(() -> cartService.addItem(testUserId, new AddToCartDTO(testProductId, 100)))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @Order(15)
    @DisplayName("addItem checks total quantity against inventory")
    void addItem_totalQuantityExceedsStock_throwsException() {
        if (inventoryService instanceof TestInventoryService testInv) {
            testInv.setAvailability(testProductId, 10);
        }

        // First add succeeds
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 8));

        // Second add would exceed available (8 + 5 = 13 > 10)
        assertThatThrownBy(() -> cartService.addItem(testUserId, new AddToCartDTO(testProductId, 5)))
                .isInstanceOf(InsufficientStockException.class);
    }

    // ==================== Update Quantity Tests ====================

    @Test
    @Order(20)
    @DisplayName("updateItemQuantity updates quantity correctly")
    void updateItemQuantity_validQuantity_updates() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        CartDTO cart = cartService.updateItemQuantity(testUserId, testProductId, 5);

        assertThat(cart.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    @Order(21)
    @DisplayName("updateItemQuantity to zero removes item")
    void updateItemQuantity_zero_removesItem() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        CartDTO cart = cartService.updateItemQuantity(testUserId, testProductId, 0);

        assertThat(cart.items()).isEmpty();
    }

    @Test
    @Order(22)
    @DisplayName("updateItemQuantity throws for non-existent item")
    void updateItemQuantity_nonExistent_throwsException() {
        assertThatThrownBy(() -> cartService.updateItemQuantity(testUserId, testProductId, 5))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    // ==================== Remove Item Tests ====================

    @Test
    @Order(30)
    @DisplayName("removeItem removes item from cart")
    void removeItem_existingItem_removesFromCart() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 1));

        CartDTO cart = cartService.removeItem(testUserId, testProductId);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().get(0).productId()).isEqualTo(testProductId2);
    }

    @Test
    @Order(31)
    @DisplayName("removeItem throws for non-existent item")
    void removeItem_nonExistent_throwsException() {
        assertThatThrownBy(() -> cartService.removeItem(testUserId, testProductId))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    // ==================== Clear Cart Tests ====================

    @Test
    @Order(40)
    @DisplayName("clearCart removes all items")
    void clearCart_withItems_clearsAll() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 1));

        CartDTO cart = cartService.clearCart(testUserId);

        assertThat(cart.items()).isEmpty();
        assertThat(cart.totalAmount()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @Order(41)
    @DisplayName("clearCart on empty cart returns empty cart")
    void clearCart_emptyCart_returnsEmptyCart() {
        CartDTO cart = cartService.clearCart(testUserId);

        assertThat(cart.items()).isEmpty();
    }

    // ==================== Cart Persistence Tests ====================

    @Test
    @Order(50)
    @DisplayName("Cart persists to database for logged-in user")
    void cart_loggedInUser_persistsToDb() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));

        Optional<CartEntity> savedCart = cartRepository.findByUserId(testUserId);

        assertThat(savedCart).isPresent();
        assertThat(savedCart.get().getUserId()).isEqualTo(testUserId);
        assertThat(savedCart.get().getItems()).hasSize(1);
    }

    @Test
    @Order(51)
    @DisplayName("Cart version increments on update")
    void cart_update_incrementsVersion() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        CartEntity cart1 = cartRepository.findByUserId(testUserId).orElseThrow();
        Long version1 = cart1.getVersion();

        cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 1));
        CartEntity cart2 = cartRepository.findByUserId(testUserId).orElseThrow();

        assertThat(cart2.getVersion()).isGreaterThan(version1);
    }

    // ==================== Price Snapshot Tests ====================

    @Test
    @Order(60)
    @DisplayName("Item price is captured at add time")
    void addItem_capturesPrice_atAddTime() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));

        CartDTO cart = cartService.getCart(testUserId);

        assertThat(cart.items().get(0).priceAtAdd()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(cart.items().get(0).productName()).isEqualTo("Test Product 1");
    }
}
