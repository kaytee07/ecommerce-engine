package com.shop.ecommerceengine.order;

import com.shop.ecommerceengine.cart.dto.AddToCartDTO;
import com.shop.ecommerceengine.cart.dto.CartDTO;
import com.shop.ecommerceengine.cart.service.CartService;
import com.shop.ecommerceengine.cart.service.InventoryServiceInterface;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import com.shop.ecommerceengine.order.dto.OrderDTO;
import com.shop.ecommerceengine.order.dto.OrderHistoryDTO;
import com.shop.ecommerceengine.order.entity.OrderEntity;
import com.shop.ecommerceengine.order.entity.OrderStatus;
import com.shop.ecommerceengine.order.exception.InvalidOrderStateException;
import com.shop.ecommerceengine.order.exception.OrderNotFoundException;
import com.shop.ecommerceengine.order.repository.OrderRepository;
import com.shop.ecommerceengine.order.service.OrderService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Order creation and state transitions.
 * Tests the full flow: cart -> order -> state changes.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderCreationIntegrationTest {

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
        private final ConcurrentMap<UUID, Integer> reserved = new ConcurrentHashMap<>();
        private int defaultQuantity = 100;

        @Override
        public boolean checkAvailability(UUID productId, int quantity) {
            int available = availability.getOrDefault(productId, defaultQuantity);
            int alreadyReserved = reserved.getOrDefault(productId, 0);
            return (available - alreadyReserved) >= quantity;
        }

        @Override
        public int getAvailableQuantity(UUID productId) {
            int available = availability.getOrDefault(productId, defaultQuantity);
            int alreadyReserved = reserved.getOrDefault(productId, 0);
            return available - alreadyReserved;
        }

        public void reserveStock(UUID productId, int quantity) {
            reserved.merge(productId, quantity, Integer::sum);
        }

        public void releaseStock(UUID productId, int quantity) {
            reserved.merge(productId, -quantity, Integer::sum);
        }

        public void setAvailability(UUID productId, int quantity) {
            availability.put(productId, quantity);
        }

        public int getReservedQuantity(UUID productId) {
            return reserved.getOrDefault(productId, 0);
        }

        public void reset() {
            availability.clear();
            reserved.clear();
            defaultQuantity = 100;
        }
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryServiceInterface inventoryService;

    private UUID testUserId;
    private UUID testProductId;
    private UUID testProductId2;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6378);
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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ordertest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.data.redis.port", () -> 6378);
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
        product1.setName("Order Test Product 1");
        product1.setSlug("order-test-product-1-" + System.currentTimeMillis());
        product1.setPrice(new BigDecimal("99.99"));
        product1.setSku("ORDER-TEST-1-" + System.currentTimeMillis());
        product1.setActive(true);
        product1 = productRepository.save(product1);
        testProductId = product1.getId();

        ProductEntity product2 = new ProductEntity();
        product2.setName("Order Test Product 2");
        product2.setSlug("order-test-product-2-" + System.currentTimeMillis());
        product2.setPrice(new BigDecimal("49.99"));
        product2.setSku("ORDER-TEST-2-" + System.currentTimeMillis());
        product2.setActive(true);
        product2 = productRepository.save(product2);
        testProductId2 = product2.getId();
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ==================== Order Creation Tests ====================

    @Test
    @Order(1)
    @DisplayName("createOrderFromCart creates order with PENDING status")
    void createOrderFromCart_validCart_createsOrderPending() {
        // Add items to cart
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));
        cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 1));

        // Create order from cart
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        assertThat(order).isNotNull();
        assertThat(order.id()).isNotNull();
        assertThat(order.userId()).isEqualTo(testUserId);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.items()).hasSize(2);
        assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("249.97")); // 2*99.99 + 1*49.99
    }

    @Test
    @Order(2)
    @DisplayName("createOrderFromCart clears cart after order creation")
    void createOrderFromCart_clearsCart_afterCreation() {
        // Add items to cart
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 2));

        // Create order from cart
        orderService.createOrderFromCart(testUserId);

        // Verify cart is empty
        CartDTO cart = cartService.getCart(testUserId);
        assertThat(cart.items()).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("createOrderFromCart throws when cart is empty")
    void createOrderFromCart_emptyCart_throwsException() {
        assertThatThrownBy(() -> orderService.createOrderFromCart(testUserId))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @Order(4)
    @DisplayName("createOrderFromCart captures price at order time")
    void createOrderFromCart_capturesPrice_atOrderTime() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));

        OrderDTO order = orderService.createOrderFromCart(testUserId);

        assertThat(order.items().get(0).priceAtOrder()).isEqualByComparingTo(new BigDecimal("99.99"));
    }

    // ==================== Order Retrieval Tests ====================

    @Test
    @Order(10)
    @DisplayName("getOrderById returns order for owner")
    void getOrderById_existingOrder_returnsOrder() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO createdOrder = orderService.createOrderFromCart(testUserId);

        OrderDTO retrievedOrder = orderService.getOrderById(createdOrder.id(), testUserId);

        assertThat(retrievedOrder).isNotNull();
        assertThat(retrievedOrder.id()).isEqualTo(createdOrder.id());
    }

    @Test
    @Order(11)
    @DisplayName("getOrderById throws for non-existent order")
    void getOrderById_nonExistent_throwsException() {
        UUID fakeOrderId = UUID.randomUUID();

        assertThatThrownBy(() -> orderService.getOrderById(fakeOrderId, testUserId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @Order(12)
    @DisplayName("getOrderById throws when user doesn't own order")
    void getOrderById_differentUser_throwsException() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO createdOrder = orderService.createOrderFromCart(testUserId);

        UUID differentUserId = UUID.randomUUID();
        assertThatThrownBy(() -> orderService.getOrderById(createdOrder.id(), differentUserId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ==================== Order History Tests ====================

    @Test
    @Order(20)
    @DisplayName("getUserOrders returns orders in descending order")
    void getUserOrders_multipleOrders_returnsDescending() {
        // Create first order
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order1 = orderService.createOrderFromCart(testUserId);

        // Create second order
        cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 2));
        OrderDTO order2 = orderService.createOrderFromCart(testUserId);

        List<OrderHistoryDTO> orders = orderService.getUserOrders(testUserId);

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).id()).isEqualTo(order2.id()); // Most recent first
        assertThat(orders.get(1).id()).isEqualTo(order1.id());
    }

    @Test
    @Order(21)
    @DisplayName("getUserOrders returns empty list for user with no orders")
    void getUserOrders_noOrders_returnsEmptyList() {
        List<OrderHistoryDTO> orders = orderService.getUserOrders(testUserId);

        assertThat(orders).isEmpty();
    }

    // ==================== State Machine Tests ====================

    @Test
    @Order(30)
    @DisplayName("updateStatus transitions PENDING to CONFIRMED")
    void updateStatus_pendingToConfirmed_succeeds() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        OrderDTO updated = orderService.updateStatus(order.id(), OrderStatus.CONFIRMED);

        assertThat(updated.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @Order(31)
    @DisplayName("updateStatus transitions CONFIRMED to PROCESSING")
    void updateStatus_confirmedToProcessing_succeeds() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);
        orderService.updateStatus(order.id(), OrderStatus.CONFIRMED);

        OrderDTO updated = orderService.updateStatus(order.id(), OrderStatus.PROCESSING);

        assertThat(updated.status()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @Order(32)
    @DisplayName("updateStatus transitions PROCESSING to SHIPPED")
    void updateStatus_processingToShipped_succeeds() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);
        orderService.updateStatus(order.id(), OrderStatus.CONFIRMED);
        orderService.updateStatus(order.id(), OrderStatus.PROCESSING);

        OrderDTO updated = orderService.updateStatus(order.id(), OrderStatus.SHIPPED);

        assertThat(updated.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @Order(33)
    @DisplayName("updateStatus transitions SHIPPED to DELIVERED")
    void updateStatus_shippedToDelivered_succeeds() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);
        orderService.updateStatus(order.id(), OrderStatus.CONFIRMED);
        orderService.updateStatus(order.id(), OrderStatus.PROCESSING);
        orderService.updateStatus(order.id(), OrderStatus.SHIPPED);

        OrderDTO updated = orderService.updateStatus(order.id(), OrderStatus.DELIVERED);

        assertThat(updated.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @Order(34)
    @DisplayName("updateStatus rejects invalid transition PENDING to SHIPPED")
    void updateStatus_invalidTransition_throwsException() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        assertThatThrownBy(() -> orderService.updateStatus(order.id(), OrderStatus.SHIPPED))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("transition");
    }

    @Test
    @Order(35)
    @DisplayName("updateStatus allows PENDING to CANCELLED")
    void updateStatus_pendingToCancelled_succeeds() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        OrderDTO updated = orderService.updateStatus(order.id(), OrderStatus.CANCELLED);

        assertThat(updated.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @Order(36)
    @DisplayName("updateStatus rejects transition from DELIVERED")
    void updateStatus_fromDelivered_throwsException() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);
        orderService.updateStatus(order.id(), OrderStatus.CONFIRMED);
        orderService.updateStatus(order.id(), OrderStatus.PROCESSING);
        orderService.updateStatus(order.id(), OrderStatus.SHIPPED);
        orderService.updateStatus(order.id(), OrderStatus.DELIVERED);

        assertThatThrownBy(() -> orderService.updateStatus(order.id(), OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    // ==================== Soft Delete Tests ====================

    @Test
    @Order(40)
    @DisplayName("Soft deleted orders are not returned in user orders")
    void softDeletedOrders_notInUserOrders() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        // Soft delete the order
        orderService.softDeleteOrder(order.id());

        List<OrderHistoryDTO> orders = orderService.getUserOrders(testUserId);
        assertThat(orders).isEmpty();
    }

    @Test
    @Order(41)
    @DisplayName("Soft deleted order throws not found on get")
    void softDeletedOrder_getById_throwsNotFound() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        orderService.softDeleteOrder(order.id());

        assertThatThrownBy(() -> orderService.getOrderById(order.id(), testUserId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ==================== Admin Tests ====================

    @Test
    @Order(50)
    @DisplayName("getAllOrders returns all non-deleted orders")
    void getAllOrders_returnsAllOrders() {
        // Create orders for different users
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        orderService.createOrderFromCart(testUserId);

        UUID anotherUser = UUID.randomUUID();
        cartService.addItem(anotherUser, new AddToCartDTO(testProductId2, 1));
        orderService.createOrderFromCart(anotherUser);

        List<OrderDTO> allOrders = orderService.getAllOrders();

        assertThat(allOrders).hasSize(2);
    }

    @Test
    @Order(51)
    @DisplayName("getOrdersByStatus returns orders with specific status")
    void getOrdersByStatus_returnsFiltered() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order1 = orderService.createOrderFromCart(testUserId);
        orderService.updateStatus(order1.id(), OrderStatus.CONFIRMED);

        cartService.addItem(testUserId, new AddToCartDTO(testProductId2, 1));
        orderService.createOrderFromCart(testUserId); // Stays PENDING

        List<OrderDTO> confirmedOrders = orderService.getOrdersByStatus(OrderStatus.CONFIRMED);
        List<OrderDTO> pendingOrders = orderService.getOrdersByStatus(OrderStatus.PENDING);

        assertThat(confirmedOrders).hasSize(1);
        assertThat(pendingOrders).hasSize(1);
    }

    @Test
    @Order(52)
    @DisplayName("Admin can update any order status")
    void adminUpdateStatus_anyOrder_succeeds() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        // Admin update (no user check)
        OrderDTO updated = orderService.adminUpdateStatus(order.id(), OrderStatus.CONFIRMED);

        assertThat(updated.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    // ==================== Version/Concurrency Tests ====================

    @Test
    @Order(60)
    @DisplayName("Order version increments on status update")
    void orderVersion_incrementsOnUpdate() {
        cartService.addItem(testUserId, new AddToCartDTO(testProductId, 1));
        OrderDTO order = orderService.createOrderFromCart(testUserId);

        OrderEntity entity1 = orderRepository.findById(order.id()).orElseThrow();
        Long version1 = entity1.getVersion();

        orderService.updateStatus(order.id(), OrderStatus.CONFIRMED);

        OrderEntity entity2 = orderRepository.findById(order.id()).orElseThrow();
        assertThat(entity2.getVersion()).isGreaterThan(version1);
    }
}
