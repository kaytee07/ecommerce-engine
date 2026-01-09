package com.shop.ecommerceengine.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ecommerceengine.catalog.controller.AdminProductController;
import com.shop.ecommerceengine.catalog.entity.CategoryEntity;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.repository.CategoryRepository;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import com.shop.ecommerceengine.catalog.service.ImageService;
import com.shop.ecommerceengine.catalog.service.ProductAuditHelper;
import com.shop.ecommerceengine.catalog.service.ProductService;
import com.shop.ecommerceengine.common.exception.GlobalExceptionHandler;
import com.shop.ecommerceengine.identity.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Phase 5: Catalog Admin.
 * Tests admin CRUD operations, discounts, RBAC, and audit logging.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminProductControllerIntegrationTest {

    private static RedisServer redisServer;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private MockMvc mockMvc;

    private static UUID testCategoryId;
    private static UUID testProductId;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6375);
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
        // H2 database (PostgreSQL mode)
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:admintest5;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");

        // Redis
        registry.add("spring.data.redis.port", () -> 6375);
        registry.add("spring.data.redis.host", () -> "localhost");

        // S3/MinIO disabled for tests
        registry.add("s3.endpoint", () -> "http://localhost:9000");
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Create test category and product if not exists
        if (testCategoryId == null) {
            CategoryEntity category = new CategoryEntity();
            category.setName("Test Category");
            category.setSlug("test-category-admin");
            category.setDescription("Test category for admin tests");
            category = categoryRepository.save(category);
            testCategoryId = category.getId();
        }

        if (testProductId == null) {
            ProductEntity product = new ProductEntity();
            product.setName("Test Product");
            product.setSlug("test-product-admin");
            product.setDescription("Test product for admin tests");
            product.setPrice(new BigDecimal("99.99"));
            product.setSku("TEST-PROD-ADMIN");
            product.setActive(true);
            product.setCategoryId(testCategoryId);
            product = productRepository.save(product);
            testProductId = product.getId();
        }
    }

    // ==================== RBAC Tests ====================

    @Test
    @Order(1)
    @DisplayName("GET /api/v1/admin/products - content manager can access")
    @WithMockUser(roles = "CONTENT_MANAGER")
    void getProducts_contentManager_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/admin/products - regular user gets 403")
    @WithMockUser(roles = "USER")
    void getProducts_regularUser_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/admin/products - no auth gets 401")
    void getProducts_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Product CRUD Tests ====================

    @Test
    @Order(10)
    @DisplayName("POST /api/v1/admin/products - create product")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void createProduct_contentManager_returnsCreated() throws Exception {
        String productJson = """
                {
                    "name": "New Admin Product",
                    "description": "Created by admin",
                    "price": 149.99,
                    "sku": "ADMIN-NEW-002"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.product.name").value("New Admin Product"));
    }

    @Test
    @Order(11)
    @DisplayName("PUT /api/v1/admin/products/{id} - update product")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void updateProduct_contentManager_returnsUpdated() throws Exception {
        String updateJson = """
                {
                    "name": "Updated Product Name",
                    "price": 129.99
                }
                """;

        mockMvc.perform(put("/api/v1/admin/products/" + testProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.product.name").value("Updated Product Name"));
    }

    @Test
    @Order(12)
    @DisplayName("PUT /api/v1/admin/products/{id} - price change >50% includes warning")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void updateProduct_largePriceChange_includesWarning() throws Exception {
        // First set a known price
        ProductEntity product = productRepository.findById(testProductId).orElseThrow();
        product.setPrice(new BigDecimal("100.00"));
        productRepository.save(product);

        // Now update with large price change (>50%) - name is required by ProductCreateDTO
        String updateJson = """
                {
                    "name": "Updated with Price Change",
                    "price": 10.00
                }
                """;

        MvcResult result = mockMvc.perform(put("/api/v1/admin/products/" + testProductId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andReturn();

        // Check for warning in message
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).containsIgnoringCase("warning");
    }

    @Test
    @Order(13)
    @DisplayName("DELETE /api/v1/admin/products/{id} - soft delete product")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void deleteProduct_contentManager_returnsSoftDeleted() throws Exception {
        // Create a product to delete
        ProductEntity product = new ProductEntity();
        product.setName("Product To Delete");
        product.setSlug("product-to-delete-" + System.currentTimeMillis());
        product.setPrice(new BigDecimal("50.00"));
        product.setSku("DELETE-" + System.currentTimeMillis());
        product.setActive(true);
        product = productRepository.save(product);

        mockMvc.perform(delete("/api/v1/admin/products/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsStringIgnoringCase("deleted")));

        // Verify soft delete
        ProductEntity deleted = productRepository.findById(product.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    // ==================== Discount Tests ====================

    @Test
    @Order(20)
    @DisplayName("POST /api/v1/admin/products/{id}/discount - set discount")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void setDiscount_validDiscount_returnsSuccess() throws Exception {
        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.plusDays(7);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        String discountJson = String.format("""
                {
                    "discountPercentage": 15,
                    "startDate": "%s",
                    "endDate": "%s"
                }
                """, start.format(formatter), end.format(formatter));

        mockMvc.perform(post("/api/v1/admin/products/" + testProductId + "/discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.discountPercentage").value(15.0));
    }

    @Test
    @Order(21)
    @DisplayName("POST /api/v1/admin/products/{id}/discount - discount >100% fails")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void setDiscount_over100Percent_returns400() throws Exception {
        String discountJson = """
                {
                    "discountPercentage": 150
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products/" + testProductId + "/discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(22)
    @DisplayName("POST /api/v1/admin/products/{id}/discount - negative discount fails")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void setDiscount_negativePercent_returns400() throws Exception {
        String discountJson = """
                {
                    "discountPercentage": -10
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products/" + testProductId + "/discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(23)
    @DisplayName("POST /api/v1/admin/products/{id}/discount - end before start fails")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void setDiscount_invalidDates_returns400() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(7);
        LocalDateTime end = LocalDateTime.now(); // End before start
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        String discountJson = String.format("""
                {
                    "discountPercentage": 10,
                    "startDate": "%s",
                    "endDate": "%s"
                }
                """, start.format(formatter), end.format(formatter));

        mockMvc.perform(post("/api/v1/admin/products/" + testProductId + "/discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value("INVALID_DISCOUNT"));
    }

    // ==================== Bulk Discount Tests ====================

    @Test
    @Order(30)
    @DisplayName("POST /api/v1/admin/products/bulk-discount - apply to category")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void bulkDiscount_byCategory_appliesDiscount() throws Exception {
        String bulkJson = String.format("""
                {
                    "categoryId": "%s",
                    "discountPercentage": 10
                }
                """, testCategoryId);

        mockMvc.perform(post("/api/v1/admin/products/bulk-discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.updatedCount").isNumber());
    }

    @Test
    @Order(31)
    @DisplayName("POST /api/v1/admin/products/bulk-discount - apply to ALL products")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void bulkDiscount_allProducts_appliesDiscount() throws Exception {
        String bulkJson = """
                {
                    "applyToAll": true,
                    "discountPercentage": 5
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products/bulk-discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.data.updatedCount").isNumber());
    }

    @Test
    @Order(32)
    @DisplayName("POST /api/v1/admin/products/bulk-discount - remove discount (0%)")
    @WithMockUser(username = "admin", roles = "CONTENT_MANAGER")
    void bulkDiscount_removeDiscount_clearsDiscount() throws Exception {
        String bulkJson = """
                {
                    "applyToAll": true,
                    "discountPercentage": 0
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products/bulk-discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));
    }

    // ==================== Super Admin Tests ====================

    @Test
    @Order(40)
    @DisplayName("Super admin can access admin products")
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdmin_canAccessAdminProducts() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(41)
    @DisplayName("Super admin can set discounts")
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void superAdmin_canSetDiscount() throws Exception {
        String discountJson = """
                {
                    "discountPercentage": 20
                }
                """;

        mockMvc.perform(post("/api/v1/admin/products/" + testProductId + "/discount")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(discountJson))
                .andExpect(status().isOk());
    }
}
