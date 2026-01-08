package com.shop.ecommerceengine.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ecommerceengine.identity.dto.*;
import com.shop.ecommerceengine.identity.entity.UserEntity;
import com.shop.ecommerceengine.identity.repository.UserRepository;
import com.shop.ecommerceengine.identity.service.VerificationTokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Phase 3: User Management.
 * Tests registration, email verification, password recovery, and admin operations.
 * Uses H2 in-memory database and embedded Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserManagementIntegrationTest {

    private static RedisServer redisServer;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenService verificationTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RestClient restClient;
    private String baseUrl;

    // Store tokens between tests
    private static String adminAccessToken;
    private static UUID createdUserId;

    @BeforeAll
    static void startRedis() throws IOException {
        redisServer = new RedisServer(6370);
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
        // Use H2 in-memory database
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.data.redis.port", () -> 6370);
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        // Clear rate limit keys to ensure test isolation
        var keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @Order(0)
    @DisplayName("Setup: Create admin user for subsequent tests")
    void setup_createAdminUser() {
        // Create a super admin user for testing admin operations
        UserEntity admin = new UserEntity();
        admin.setUsername("superadmin");
        admin.setEmail("admin@test.com");
        admin.setPassword(passwordEncoder.encode("Admin123!"));
        admin.setFullName("Super Admin");
        admin.setRoles(new HashSet<>(Set.of("ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_USER")));
        admin.setEmailVerified(true);
        admin.setEnabled(true);
        admin.setLocked(false);

        userRepository.save(admin);

        // Login as admin to get token
        LoginRequest loginRequest = new LoginRequest("superadmin", "Admin123!");

        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        try {
            JsonNode body = objectMapper.readTree(response.getBody());
            adminAccessToken = body.get("data").get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse admin login response", e);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Register user with valid data succeeds")
    void register_withValidData_succeeds() throws Exception {
        // Given
        UserCreateDTO request = new UserCreateDTO(
                "newuser",
                "newuser@test.com",
                "Password123!",
                "New User"
        );

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("user").get("username").asText()).isEqualTo("newuser");
        assertThat(body.get("data").get("user").get("email").asText()).isEqualTo("newuser@test.com");
        assertThat(body.get("data").get("user").get("email_verified").asBoolean()).isFalse();

        // Store user ID for later tests
        createdUserId = UUID.fromString(body.get("data").get("user").get("id").asText());

        // Verify user exists in database
        UserEntity user = userRepository.findByUsername("newuser").orElseThrow();
        assertThat(user.getRoles()).contains("ROLE_USER");
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("Register with existing username fails")
    void register_withExistingUsername_fails() {
        // Given
        UserCreateDTO request = new UserCreateDTO(
                "newuser", // Same username as previous test
                "different@test.com",
                "Password123!",
                "Another User"
        );

        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @Order(3)
    @DisplayName("Register with existing email fails")
    void register_withExistingEmail_fails() {
        // Given
        UserCreateDTO request = new UserCreateDTO(
                "anotheruser",
                "newuser@test.com", // Same email as previous test
                "Password123!",
                "Another User"
        );

        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @Order(4)
    @DisplayName("Register with weak password fails validation")
    void register_withWeakPassword_fails() {
        // Given - password without uppercase
        UserCreateDTO request = new UserCreateDTO(
                "weakuser",
                "weak@test.com",
                "password123", // No uppercase
                "Weak User"
        );

        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @Order(5)
    @DisplayName("Email verification with valid token succeeds")
    void verifyEmail_withValidToken_succeeds() {
        // Given - generate a verification token for our test user
        String token = verificationTokenService.generateEmailVerificationToken("newuser");

        // When
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/auth/verify-email?token=" + token)
                .retrieve()
                .toEntity(String.class);

        // Then - should redirect (302)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);

        // Verify user is now email verified
        UserEntity user = userRepository.findByUsername("newuser").orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Email verification with invalid token fails")
    void verifyEmail_withInvalidToken_fails() {
        // Given
        String invalidToken = UUID.randomUUID().toString();

        // When/Then
        assertThatThrownBy(() -> restClient.get()
                .uri("/api/v1/auth/verify-email?token=" + invalidToken)
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
    @DisplayName("Forgot password always returns success (no enumeration)")
    void forgotPassword_alwaysReturnsSuccess() throws Exception {
        // Given - existing email
        ForgotPasswordRequest request = new ForgotPasswordRequest("newuser@test.com");

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Also test with non-existent email - should still return 200
        ForgotPasswordRequest nonExistent = new ForgotPasswordRequest("nonexistent@test.com");

        ResponseEntity<String> response2 = restClient.post()
                .uri("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(nonExistent)
                .retrieve()
                .toEntity(String.class);

        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(8)
    @DisplayName("Reset password with valid token succeeds")
    void resetPassword_withValidToken_succeeds() throws Exception {
        // Given - generate reset token
        String token = verificationTokenService.generatePasswordResetToken("newuser");
        ResetPasswordRequest request = new ResetPasswordRequest(token, "NewPassword123!");

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify new password works
        LoginRequest loginRequest = new LoginRequest("newuser", "NewPassword123!");
        ResponseEntity<String> loginResponse = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(9)
    @DisplayName("Reset password with invalid token fails")
    void resetPassword_withInvalidToken_fails() {
        // Given
        ResetPasswordRequest request = new ResetPasswordRequest(
                UUID.randomUUID().toString(),
                "NewPassword123!"
        );

        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    @Order(10)
    @DisplayName("Admin can list all users")
    void adminListUsers_succeeds() throws Exception {
        Assumptions.assumeTrue(adminAccessToken != null, "Admin token required");

        // When
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("status").asBoolean()).isTrue();
        assertThat(body.get("data").get("content").isArray()).isTrue();
    }

    @Test
    @Order(11)
    @DisplayName("Admin can assign roles to user")
    void adminAssignRoles_succeeds() throws Exception {
        Assumptions.assumeTrue(adminAccessToken != null, "Admin token required");
        Assumptions.assumeTrue(createdUserId != null, "User ID required");

        // Given
        RoleAssignDTO request = new RoleAssignDTO(Set.of("ROLE_USER", "ROLE_CONTENT_MANAGER"));

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/admin/users/" + createdUserId + "/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify roles updated in database
        UserEntity user = userRepository.findById(createdUserId).orElseThrow();
        assertThat(user.getRoles()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_CONTENT_MANAGER");
    }

    @Test
    @Order(12)
    @DisplayName("Non-admin cannot assign roles")
    void nonAdminAssignRoles_fails() throws Exception {
        Assumptions.assumeTrue(createdUserId != null, "User ID required");

        // Login as regular user
        LoginRequest loginRequest = new LoginRequest("newuser", "NewPassword123!");
        ResponseEntity<String> loginResponse = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class);

        JsonNode loginBody = objectMapper.readTree(loginResponse.getBody());
        String userToken = loginBody.get("data").get("access_token").asText();

        // Given
        RoleAssignDTO request = new RoleAssignDTO(Set.of("ROLE_ADMIN"));

        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/admin/users/" + createdUserId + "/roles")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    assertThat(httpEx.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @Order(13)
    @DisplayName("Admin can disable user")
    void adminDisableUser_succeeds() throws Exception {
        Assumptions.assumeTrue(adminAccessToken != null, "Admin token required");
        Assumptions.assumeTrue(createdUserId != null, "User ID required");

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/admin/users/" + createdUserId + "/disable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify user is disabled
        UserEntity user = userRepository.findById(createdUserId).orElseThrow();
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    @Order(14)
    @DisplayName("Disabled user cannot login")
    void disabledUser_cannotLogin() {
        // Given
        LoginRequest loginRequest = new LoginRequest("newuser", "NewPassword123!");

        // When/Then
        assertThatThrownBy(() -> restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpClientErrorException httpEx = (HttpClientErrorException) ex;
                    // Should be unauthorized since user is disabled
                    assertThat(httpEx.getStatusCode().is4xxClientError()).isTrue();
                });
    }

    @Test
    @Order(15)
    @DisplayName("Admin can enable user")
    void adminEnableUser_succeeds() throws Exception {
        Assumptions.assumeTrue(adminAccessToken != null, "Admin token required");
        Assumptions.assumeTrue(createdUserId != null, "User ID required");

        // When
        ResponseEntity<String> response = restClient.post()
                .uri("/api/v1/admin/users/" + createdUserId + "/enable")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify user is enabled
        UserEntity user = userRepository.findById(createdUserId).orElseThrow();
        assertThat(user.isEnabled()).isTrue();

        // Verify user can login again
        LoginRequest loginRequest = new LoginRequest("newuser", "NewPassword123!");
        ResponseEntity<String> loginResponse = restClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequest)
                .retrieve()
                .toEntity(String.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(16)
    @DisplayName("Admin can search users")
    void adminSearchUsers_succeeds() throws Exception {
        Assumptions.assumeTrue(adminAccessToken != null, "Admin token required");

        // When
        ResponseEntity<String> response = restClient.get()
                .uri("/api/v1/admin/users/search?q=newuser")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAccessToken)
                .retrieve()
                .toEntity(String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("data").get("content").size()).isGreaterThan(0);
    }
}
