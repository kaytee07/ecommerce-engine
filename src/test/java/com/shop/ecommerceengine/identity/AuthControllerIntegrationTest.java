package com.shop.ecommerceengine.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ecommerceengine.identity.dto.LoginRequest;
import com.shop.ecommerceengine.identity.entity.UserEntity;
import com.shop.ecommerceengine.identity.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for authentication endpoints.
 * Uses embedded Redis for token storage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthControllerIntegrationTest {

    private static RedisServer redisServer;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RestClient restClient;
    private String baseUrl;

    // Store tokens between tests
    private static String accessToken;
    private static String refreshTokenValue;
    private static boolean usersCreated = false;

    @BeforeAll
    void startRedisAndCreateUsers() throws IOException {
        redisServer = new RedisServer(6370);
        redisServer.start();

        // Create test users if not already created
        if (!usersCreated) {
            createTestUsers();
            usersCreated = true;
        }
    }

    @AfterAll
    void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    private void createTestUsers() {
        // Create regular test user
        if (userRepository.findByUsername("testuser").isEmpty()) {
            UserEntity testUser = new UserEntity();
            testUser.setUsername("testuser");
            testUser.setEmail("testuser@test.com");
            testUser.setPassword(passwordEncoder.encode("password123"));
            testUser.setFullName("Test User");
            testUser.setEmailVerified(true);
            testUser.setEnabled(true);
            testUser.setRoles(new HashSet<>(Set.of("ROLE_USER")));
            userRepository.save(testUser);
        }

        // Create admin user
        if (userRepository.findByUsername("admin").isEmpty()) {
            UserEntity adminUser = new UserEntity();
            adminUser.setUsername("admin");
            adminUser.setEmail("admin@test.com");
            adminUser.setPassword(passwordEncoder.encode("admin123"));
            adminUser.setFullName("Admin User");
            adminUser.setEmailVerified(true);
            adminUser.setEnabled(true);
            adminUser.setRoles(new HashSet<>(Set.of("ROLE_USER", "ROLE_ADMIN")));
            userRepository.save(adminUser);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis configuration
        registry.add("spring.data.redis.port", () -> 6370);
        registry.add("spring.data.redis.host", () -> "localhost");

        // H2 database configuration (PostgreSQL mode)
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:authtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("Login with valid credentials returns access token and sets refresh cookie")
    void login_withValidCredentials_returnsTokens() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("access_token").asText()).isNotEmpty();
        assertThat(body.get("data").get("token_type").asText()).isEqualTo("Bearer");
        assertThat(body.get("data").get("expires_in").asLong()).isGreaterThan(0);

        // Store access token for subsequent tests
        accessToken = body.get("data").get("access_token").asText();

        // Check refresh token cookie
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();

        String refreshCookie = cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .orElse(null);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).contains("HttpOnly");

        // Extract just the token value
        refreshTokenValue = refreshCookie.split(";")[0].split("=")[1];
    }

    @Test
    @Order(2)
    @DisplayName("Login with invalid credentials returns 401")
    void login_withInvalidCredentials_returns401() {
        // Given
        LoginRequest loginRequest = new LoginRequest("testuser", "wrongpassword");

        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @Order(3)
    @DisplayName("Get current user with valid token returns user info")
    void getCurrentUser_withValidToken_returnsUserInfo() throws Exception {
        // Skip if no token from previous test
        Assumptions.assumeTrue(accessToken != null, "Access token required from login test");

        // When
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("username").asText()).isEqualTo("testuser");
        assertThat(body.get("data").get("roles")).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("Get current user without token returns 401")
    void getCurrentUser_withoutToken_returns401() {
        // When/Then
        assertThatThrownBy(() -> restClient.get()
                .uri("/api/v1/auth/me")
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @Order(5)
    @DisplayName("Refresh token returns new access token")
    void refresh_withValidCookie_returnsNewToken() throws Exception {
        // Skip if no token from previous test
        Assumptions.assumeTrue(refreshTokenValue != null, "Refresh token required from login test");

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/refresh")
                .header(HttpHeaders.COOKIE, "refresh_token=" + refreshTokenValue)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("access_token").asText()).isNotEmpty();

        // Should get new refresh cookie (rotation)
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();

        String newRefreshCookie = cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .orElse(null);
        assertThat(newRefreshCookie).isNotNull();

        // Update for subsequent tests
        refreshTokenValue = newRefreshCookie.split(";")[0].split("=")[1];
    }

    @Test
    @Order(6)
    @DisplayName("Refresh without cookie returns 401")
    void refresh_withoutCookie_returns401() {
        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/auth/refresh")
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @Order(7)
    @DisplayName("Admin login with valid credentials returns token with admin roles")
    void adminLogin_withValidCredentials_returnsTokenWithAdminRoles() throws Exception {
        // Given
        LoginRequest loginRequest = new LoginRequest("admin", "admin123");

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        String scope = body.get("data").get("scope").asText();
        assertThat(scope).contains("ROLE_ADMIN");
        assertThat(scope).contains("ROLE_USER");
    }

    @Test
    @Order(8)
    @DisplayName("Logout invalidates tokens")
    void logout_invalidatesTokens() throws Exception {
        // First login to get fresh tokens
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");

        ResponseEntity<String> loginResponse = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class);

        JsonNode loginBody = objectMapper.readTree(loginResponse.getBody());
        String token = loginBody.get("data").get("access_token").asText();

        List<String> cookies = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        String cookie = cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .map(c -> c.split(";")[0])
                .findFirst()
                .orElse("");

        // Now logout
        ResponseEntity<String> logoutResponse = restClient.post()
                .uri("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.COOKIE, cookie)
                .retrieve()
                .toEntity(String.class);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode logoutBody = objectMapper.readTree(logoutResponse.getBody());
        assertThat(logoutBody.get("data").get("success").asBoolean()).isTrue();

        // Verify token is now blacklisted - using it should fail
        assertThatThrownBy(() -> restClient.get()
                .uri("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @Order(9)
    @DisplayName("OAuth2 token endpoint rejects password grant")
    void oauth2Token_passwordGrant_rejected() throws Exception {
        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/oauth2/token?grant_type=password&username=testuser&password=password123")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(httpEx.getResponseBodyAsString()).contains("unsupported_grant_type");
                });
    }

    @Test
    @Order(10)
    @DisplayName("Multiple logins work correctly")
    void multipleLogins_workCorrectly() throws Exception {
        for (int i = 0; i < 3; i++) {
            LoginRequest loginRequest = new LoginRequest("testuser", "password123");

            ResponseEntity<String> response = restClient.post()
                    .uri("/api/v1/auth/login")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(loginRequest)
                    .retrieve()
                    .toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
