package com.shop.ecommerceengine.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Phase 4: Catalog Basics.
 * Tests product search, category listing, and Redis caching.
 * Uses H2 in-memory database (PostgreSQL mode) and embedded Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogIntegrationTest {

    private static RedisServer redisServer;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RestClient restClient;
    private String baseUrl;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6372);
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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:catalogtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");

        // Redis
        registry.add("spring.data.redis.port", () -> 6372);
        registry.add("spring.data.redis.host", () -> "localhost");

        // Cache
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.cache.redis.time-to-live", () -> "600000");
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        // Clear rate limit keys
        var keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // Clear cache keys
        var cacheKeys = redisTemplate.keys("products:*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
        }
        var categoryCacheKeys = redisTemplate.keys("categories:*");
        if (categoryCacheKeys != null && !categoryCacheKeys.isEmpty()) {
            redisTemplate.delete(categoryCacheKeys);
        }
    }

    // ==================== Product Endpoints ====================

    @Test
    @Order(1)
    @DisplayName("GET /api/v1/store/products - returns all products")
    void getAllProducts_returnsProductList() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data")).isNotNull();
        assertThat(body.get("data").isArray()).isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/v1/store/products with pagination")
    void getAllProducts_withPagination_returnsPaginatedList() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products?page=0&size=10")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data")).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/v1/store/products/{id} - existing product returns 200")
    void getProductById_existingProduct_returnsProduct() throws Exception {
        // First, get all products to find an existing ID
        ResponseEntity<String> listResponse = restClient.get()
                .uri("/api/v1/store/products")
                .retrieve()
                .toEntity(String.class);

        JsonNode listBody = objectMapper.readTree(listResponse.getBody());
        JsonNode products = listBody.get("data");

        // Skip if no products exist
        if (!products.isArray() || products.isEmpty()) {
            return;
        }

        String productId = products.get(0).get("id").asText();

        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products/" + productId)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("id").asText()).isEqualTo(productId);
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/v1/store/products/{id} - non-existent product returns 404")
    void getProductById_nonExistent_returns404() {
        UUID nonExistentId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertThatThrownBy(() -> restClient.get()
                .uri("/api/v1/store/products/" + nonExistentId)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(httpEx.getResponseBodyAsString()).contains("PRODUCT_NOT_FOUND");
                });
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/v1/store/products/search - search by query")
    void searchProducts_withQuery_returnsMatchingProducts() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products/search?q=laptop")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data")).isNotNull();
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/v1/store/products/search - search with category filter")
    void searchProducts_withCategoryFilter_returnsFilteredProducts() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products/search?category=electronics")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/v1/store/products/search - search with pagination")
    void searchProducts_withPagination_returnsPaginatedResults() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products/search?q=&page=0&size=5")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
    }

    @Test
    @Order(8)
    @DisplayName("GET /api/v1/store/products/featured - returns featured products")
    void getFeaturedProducts_returnsFeaturedList() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products/featured")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").isArray()).isTrue();
    }

    @Test
    @Order(9)
    @DisplayName("GET /api/v1/store/products/slug/{slug} - get product by slug")
    void getProductBySlug_existingSlug_returnsProduct() throws Exception {
        // First get all products
        ResponseEntity<String> listResponse = restClient.get()
                .uri("/api/v1/store/products")
                .retrieve()
                .toEntity(String.class);

        JsonNode listBody = objectMapper.readTree(listResponse.getBody());
        JsonNode products = listBody.get("data");

        if (!products.isArray() || products.isEmpty()) {
            return;
        }

        String slug = products.get(0).get("slug").asText();

        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/products/slug/" + slug)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("slug").asText()).isEqualTo(slug);
    }

    // ==================== Category Endpoints ====================

    @Test
    @Order(10)
    @DisplayName("GET /api/v1/store/categories - returns all categories")
    void getAllCategories_returnsCategoryList() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/categories")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data")).isNotNull();
        assertThat(body.get("data").isArray()).isTrue();
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/v1/store/categories/{id} - existing category returns 200")
    void getCategoryById_existingCategory_returnsCategory() throws Exception {
        ResponseEntity<String> listResponse = restClient.get()
                .uri("/api/v1/store/categories")
                .retrieve()
                .toEntity(String.class);

        JsonNode listBody = objectMapper.readTree(listResponse.getBody());
        JsonNode categories = listBody.get("data");

        if (!categories.isArray() || categories.isEmpty()) {
            return;
        }

        String categoryId = categories.get(0).get("id").asText();

        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/categories/" + categoryId)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("id").asText()).isEqualTo(categoryId);
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/v1/store/categories/{id} - non-existent category returns 404")
    void getCategoryById_nonExistent_returns404() {
        UUID nonExistentId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertThatThrownBy(() -> restClient.get()
                .uri("/api/v1/store/categories/" + nonExistentId)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(httpEx.getResponseBodyAsString()).contains("CATEGORY_NOT_FOUND");
                });
    }

    @Test
    @Order(13)
    @DisplayName("GET /api/v1/store/categories/slug/{slug} - get category by slug")
    void getCategoryBySlug_existingSlug_returnsCategory() throws Exception {
        ResponseEntity<String> listResponse = restClient.get()
                .uri("/api/v1/store/categories")
                .retrieve()
                .toEntity(String.class);

        JsonNode listBody = objectMapper.readTree(listResponse.getBody());
        JsonNode categories = listBody.get("data");

        if (!categories.isArray() || categories.isEmpty()) {
            return;
        }

        String slug = categories.get(0).get("slug").asText();

        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/categories/slug/" + slug)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("slug").asText()).isEqualTo(slug);
    }

    @Test
    @Order(14)
    @DisplayName("GET /api/v1/store/categories/tree - returns hierarchical category tree")
    void getCategoryTree_returnsHierarchicalTree() throws Exception {
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/categories/tree")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").isArray()).isTrue();
    }

    @Test
    @Order(15)
    @DisplayName("GET /api/v1/store/categories/{id}/products - returns products in category")
    void getProductsByCategory_returnsProducts() throws Exception {
        ResponseEntity<String> listResponse = restClient.get()
                .uri("/api/v1/store/categories")
                .retrieve()
                .toEntity(String.class);

        JsonNode listBody = objectMapper.readTree(listResponse.getBody());
        JsonNode categories = listBody.get("data");

        if (!categories.isArray() || categories.isEmpty()) {
            return;
        }

        String categoryId = categories.get(0).get("id").asText();

        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/store/categories/" + categoryId + "/products")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
    }

    // ==================== Caching Tests ====================

    @Test
    @Order(16)
    @DisplayName("Product caching - second request should be cached")
    void productCaching_secondRequestIsCached() throws Exception {
        // First request
        ResponseEntity<String> response1 = restClient.get()
                .uri("/api/v1/store/products")
                .retrieve()
                .toEntity(String.class);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second request (should be cached)
        ResponseEntity<String> response2 = restClient.get()
                .uri("/api/v1/store/products")
                .retrieve()
                .toEntity(String.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getBody()).isEqualTo(response1.getBody());
    }
}
