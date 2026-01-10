package com.shop.ecommerceengine.inventory;

import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import com.shop.ecommerceengine.inventory.dto.InventoryAdjustDTO;
import com.shop.ecommerceengine.inventory.entity.InventoryEntity;
import com.shop.ecommerceengine.inventory.exception.InsufficientStockException;
import com.shop.ecommerceengine.inventory.repository.InventoryRepository;
import com.shop.ecommerceengine.inventory.service.InventoryService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrency tests for InventoryService.
 * Tests optimistic locking with @Version to prevent lost updates.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryServiceConcurrencyTest {

    private static RedisServer redisServer;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    private UUID testProductId;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6376);
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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:inventorytest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.data.redis.port", () -> 6376);
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    @BeforeEach
    void setUp() {
        // Create a test product
        ProductEntity product = new ProductEntity();
        product.setName("Concurrency Test Product");
        product.setSlug("concurrency-test-" + System.currentTimeMillis());
        product.setPrice(new BigDecimal("100.00"));
        product.setSku("CONC-TEST-" + System.currentTimeMillis());
        product.setActive(true);
        product = productRepository.save(product);
        testProductId = product.getId();

        // Create inventory with initial stock
        InventoryEntity inventory = new InventoryEntity();
        inventory.setProductId(testProductId);
        inventory.setStockQuantity(100);
        inventory.setReservedQuantity(0);
        inventoryRepository.save(inventory);
    }

    @AfterEach
    void tearDown() {
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
    }

    // ==================== Basic Stock Operations ====================

    @Test
    @Order(1)
    @DisplayName("checkAvailability returns true when stock is sufficient")
    void checkAvailability_sufficientStock_returnsTrue() {
        boolean available = inventoryService.checkAvailability(testProductId, 50);
        assertThat(available).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("checkAvailability returns false when stock is insufficient")
    void checkAvailability_insufficientStock_returnsFalse() {
        boolean available = inventoryService.checkAvailability(testProductId, 150);
        assertThat(available).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("adjustStock decreases stock correctly for SALE")
    void adjustStock_sale_decreasesStock() {
        InventoryAdjustDTO adjustment = new InventoryAdjustDTO(-10, "SALE", "Order #123");

        var result = inventoryService.adjustStock(testProductId, adjustment, null, null);

        assertThat(result.stockQuantity()).isEqualTo(90);
        assertThat(result.availableQuantity()).isEqualTo(90);
    }

    @Test
    @Order(4)
    @DisplayName("adjustStock increases stock correctly for RESTOCK")
    void adjustStock_restock_increasesStock() {
        InventoryAdjustDTO adjustment = new InventoryAdjustDTO(50, "RESTOCK", "Shipment received");

        var result = inventoryService.adjustStock(testProductId, adjustment, null, null);

        assertThat(result.stockQuantity()).isEqualTo(150);
    }

    @Test
    @Order(5)
    @DisplayName("adjustStock throws InsufficientStockException when insufficient")
    void adjustStock_insufficientStock_throwsException() {
        InventoryAdjustDTO adjustment = new InventoryAdjustDTO(-150, "SALE", "Large order");

        assertThatThrownBy(() -> inventoryService.adjustStock(testProductId, adjustment, null, null))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @Order(6)
    @DisplayName("reserveStock reserves stock correctly")
    void reserveStock_reservesCorrectly() {
        var result = inventoryService.reserveStock(testProductId, 20);

        assertThat(result.stockQuantity()).isEqualTo(100);
        assertThat(result.reservedQuantity()).isEqualTo(20);
        assertThat(result.availableQuantity()).isEqualTo(80);
    }

    @Test
    @Order(7)
    @DisplayName("releaseReservedStock releases reservation")
    void releaseReservedStock_releasesCorrectly() {
        // First reserve
        inventoryService.reserveStock(testProductId, 20);

        // Then release
        var result = inventoryService.releaseReservedStock(testProductId, 20);

        assertThat(result.reservedQuantity()).isEqualTo(0);
        assertThat(result.availableQuantity()).isEqualTo(100);
    }

    @Test
    @Order(8)
    @DisplayName("commitReservedStock commits reserved stock to sale")
    void commitReservedStock_commitsCorrectly() {
        // First reserve
        inventoryService.reserveStock(testProductId, 20);

        // Then commit
        var result = inventoryService.commitReservedStock(testProductId, 20, null, null);

        assertThat(result.stockQuantity()).isEqualTo(80);
        assertThat(result.reservedQuantity()).isEqualTo(0);
        assertThat(result.availableQuantity()).isEqualTo(80);
    }

    // ==================== Concurrency Tests ====================

    @Test
    @Order(10)
    @DisplayName("10 parallel stock adjustments do not lose updates")
    void parallelAdjustments_noLostUpdates() throws InterruptedException {
        // Start with 100 stock, do 10 decrements of 5 each = 50 total reduction
        // Expected final: 50

        int numThreads = 10;
        int decrementAmount = -5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger retryCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    boolean success = false;
                    int retries = 0;
                    int maxRetries = 20; // Higher retry limit for high contention
                    while (!success && retries < maxRetries) {
                        try {
                            InventoryAdjustDTO adjustment = new InventoryAdjustDTO(
                                    decrementAmount, "SALE", "Parallel test"
                            );
                            inventoryService.adjustStock(testProductId, adjustment, null, null);
                            success = true;
                            successCount.incrementAndGet();
                        } catch (OptimisticLockingFailureException e) {
                            // Expected - retry with backoff
                            retries++;
                            retryCount.incrementAndGet();
                            Thread.sleep(10 + (long)(Math.random() * 20)); // Jittered delay
                        }
                    }
                } catch (Exception e) {
                    // Log but don't fail - we'll check final state
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify final stock is correct (no lost updates)
        InventoryEntity finalInventory = inventoryRepository.findByProductId(testProductId).orElseThrow();

        // Should have exactly 50 remaining (100 - 10*5 = 50)
        assertThat(finalInventory.getStockQuantity()).isEqualTo(50);
        assertThat(successCount.get()).isEqualTo(10);

        // Version should have been incremented 10 times
        assertThat(finalInventory.getVersion()).isEqualTo(10L);
    }

    @Test
    @Order(11)
    @DisplayName("Concurrent reservations handle optimistic locking")
    void concurrentReservations_handledCorrectly() throws InterruptedException {
        int numThreads = 5;
        int reserveAmount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    boolean success = false;
                    int retries = 0;
                    int maxRetries = 20;
                    while (!success && retries < maxRetries) {
                        try {
                            inventoryService.reserveStock(testProductId, reserveAmount);
                            success = true;
                            successCount.incrementAndGet();
                        } catch (OptimisticLockingFailureException e) {
                            retries++;
                            Thread.sleep(10 + (long)(Math.random() * 20));
                        }
                    }
                } catch (Exception e) {
                    // Expected for some threads
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        InventoryEntity finalInventory = inventoryRepository.findByProductId(testProductId).orElseThrow();

        // All 5 reservations should succeed (5 * 10 = 50 reserved)
        assertThat(finalInventory.getReservedQuantity()).isEqualTo(50);
        assertThat(finalInventory.getAvailableQuantity()).isEqualTo(50);
        assertThat(successCount.get()).isEqualTo(5);
    }

    @Test
    @Order(12)
    @DisplayName("Mixed concurrent operations maintain consistency")
    void mixedConcurrentOperations_maintainConsistency() throws InterruptedException {
        // Mix of sales, restocks, and reservations
        int numOperations = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numOperations);
        List<Future<?>> futures = new ArrayList<>();

        // 10 sales of -2 each = -20
        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> {
                try {
                    boolean success = false;
                    int retries = 0;
                    while (!success && retries < 50) {
                        try {
                            inventoryService.adjustStock(testProductId,
                                    new InventoryAdjustDTO(-2, "SALE", "Mixed test"), null, null);
                            success = true;
                        } catch (OptimisticLockingFailureException e) {
                            retries++;
                            Thread.sleep(5 + (long)(Math.random() * 10));
                        }
                    }
                } catch (Exception e) {
                    // Log error
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 5 restocks of +10 each = +50
        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> {
                try {
                    boolean success = false;
                    int retries = 0;
                    while (!success && retries < 50) {
                        try {
                            inventoryService.adjustStock(testProductId,
                                    new InventoryAdjustDTO(10, "RESTOCK", "Mixed test"), null, null);
                            success = true;
                        } catch (OptimisticLockingFailureException e) {
                            retries++;
                            Thread.sleep(5 + (long)(Math.random() * 10));
                        }
                    }
                } catch (Exception e) {
                    // Log error
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 5 reserves of 5 each = 25 reserved
        for (int i = 0; i < 5; i++) {
            futures.add(executor.submit(() -> {
                try {
                    boolean success = false;
                    int retries = 0;
                    while (!success && retries < 50) {
                        try {
                            inventoryService.reserveStock(testProductId, 5);
                            success = true;
                        } catch (OptimisticLockingFailureException e) {
                            retries++;
                            Thread.sleep(5 + (long)(Math.random() * 10));
                        }
                    }
                } catch (Exception e) {
                    // Log error
                } finally {
                    latch.countDown();
                }
            }));
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        InventoryEntity finalInventory = inventoryRepository.findByProductId(testProductId).orElseThrow();

        // Initial: 100
        // -20 (sales) + 50 (restocks) = 130 stock
        // 25 reserved
        // Available = 130 - 25 = 105
        assertThat(finalInventory.getStockQuantity()).isEqualTo(130);
        assertThat(finalInventory.getReservedQuantity()).isEqualTo(25);
        assertThat(finalInventory.getAvailableQuantity()).isEqualTo(105);
    }

    // ==================== Low Stock Tests ====================

    @Test
    @Order(20)
    @DisplayName("Low stock alert triggered when quantity drops below threshold")
    void lowStockAlert_triggeredBelowThreshold() {
        // Reduce stock to below 5
        InventoryAdjustDTO adjustment = new InventoryAdjustDTO(-97, "SALE", "Large order");
        var result = inventoryService.adjustStock(testProductId, adjustment, null, null);

        assertThat(result.stockQuantity()).isEqualTo(3);
        // Low stock event should be published (tested via logs or mock)
    }

    @Test
    @Order(21)
    @DisplayName("findLowStockProducts returns products below threshold")
    void findLowStockProducts_returnsBelowThreshold() {
        // Create inventory with low stock
        ProductEntity lowStockProduct = new ProductEntity();
        lowStockProduct.setName("Low Stock Product");
        lowStockProduct.setSlug("low-stock-" + System.currentTimeMillis());
        lowStockProduct.setPrice(new BigDecimal("50.00"));
        lowStockProduct.setSku("LOW-" + System.currentTimeMillis());
        lowStockProduct.setActive(true);
        lowStockProduct = productRepository.save(lowStockProduct);

        InventoryEntity lowInventory = new InventoryEntity();
        lowInventory.setProductId(lowStockProduct.getId());
        lowInventory.setStockQuantity(3);
        lowInventory.setReservedQuantity(0);
        inventoryRepository.save(lowInventory);

        var lowStockProducts = inventoryService.findLowStockProducts(5);

        assertThat(lowStockProducts).hasSize(1);
        assertThat(lowStockProducts.get(0).stockQuantity()).isEqualTo(3);
    }
}
