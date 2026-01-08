package com.shop.ecommerceengine.common;

import com.shop.ecommerceengine.common.exception.RateLimitExceededException;
import com.shop.ecommerceengine.common.service.RateLimitService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for RateLimitService.
 * Uses embedded Redis for testing rate limiting functionality.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RateLimitServiceTest {

    private static RedisServer redisServer;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeAll
    void startRedis() throws IOException {
        redisServer = new RedisServer(6371);
        redisServer.start();
    }

    @AfterAll
    void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Redis configuration
        registry.add("spring.data.redis.port", () -> 6371);
        registry.add("spring.data.redis.host", () -> "localhost");

        // H2 database configuration (PostgreSQL mode)
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ratelimittest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @BeforeEach
    void clearRateLimits() {
        // Clear all rate limit keys before each test
        var keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("Rate limit allows requests within limit")
    void checkRateLimit_withinLimit_allowsRequests() {
        String clientIp = "192.168.1.100";

        // Should allow all 10 login requests (limit is 10 per minute)
        for (int i = 0; i < 10; i++) {
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN);
        }

        // Verify remaining requests
        int remaining = rateLimitService.getRemainingRequests(clientIp, RateLimitService.RateLimitType.LOGIN);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("Rate limit throws exception when exceeded")
    void checkRateLimit_exceedsLimit_throwsException() {
        String clientIp = "192.168.1.101";

        // Exhaust the limit (10 requests for login)
        for (int i = 0; i < 10; i++) {
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN);
        }

        // 11th request should fail
        assertThatThrownBy(() ->
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN)
        )
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessageContaining("Too many requests");
    }

    @Test
    @DisplayName("Different limit types have different limits")
    void checkRateLimit_differentTypes_haveDifferentLimits() {
        String clientIp = "192.168.1.102";

        // Register has limit of 5
        for (int i = 0; i < 5; i++) {
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.REGISTER);
        }

        // 6th register request should fail
        assertThatThrownBy(() ->
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.REGISTER)
        )
        .isInstanceOf(RateLimitExceededException.class);

        // But login should still work (different rate limit type)
        rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN);
    }

    @Test
    @DisplayName("Different clients have separate limits")
    void checkRateLimit_differentClients_haveSeparateLimits() {
        String client1 = "192.168.1.103";
        String client2 = "192.168.1.104";

        // Exhaust limit for client1
        for (int i = 0; i < 10; i++) {
            rateLimitService.checkRateLimit(client1, RateLimitService.RateLimitType.LOGIN);
        }

        // Client1 should be rate limited
        assertThatThrownBy(() ->
            rateLimitService.checkRateLimit(client1, RateLimitService.RateLimitType.LOGIN)
        )
        .isInstanceOf(RateLimitExceededException.class);

        // Client2 should still be able to make requests
        rateLimitService.checkRateLimit(client2, RateLimitService.RateLimitType.LOGIN);
    }

    @Test
    @DisplayName("Password reset has stricter limits")
    void checkRateLimit_passwordReset_hasStricterLimit() {
        String clientIp = "192.168.1.105";

        // Password reset has limit of 3
        for (int i = 0; i < 3; i++) {
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.PASSWORD_RESET);
        }

        // 4th request should fail
        assertThatThrownBy(() ->
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.PASSWORD_RESET)
        )
        .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    @DisplayName("Reset rate limit clears the counter")
    void resetRateLimit_clearsCounter() {
        String clientIp = "192.168.1.106";

        // Exhaust the limit
        for (int i = 0; i < 10; i++) {
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN);
        }

        // Verify rate limited
        assertThatThrownBy(() ->
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN)
        )
        .isInstanceOf(RateLimitExceededException.class);

        // Reset the limit
        rateLimitService.resetRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN);

        // Should be able to make requests again
        rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN);

        int remaining = rateLimitService.getRemainingRequests(clientIp, RateLimitService.RateLimitType.LOGIN);
        assertThat(remaining).isEqualTo(9);
    }

    @Test
    @DisplayName("Get remaining requests returns correct count")
    void getRemainingRequests_returnsCorrectCount() {
        String clientIp = "192.168.1.107";

        // Initially should have full limit
        int remaining = rateLimitService.getRemainingRequests(clientIp, RateLimitService.RateLimitType.LOGIN);
        assertThat(remaining).isEqualTo(10);

        // Make 3 requests
        for (int i = 0; i < 3; i++) {
            rateLimitService.checkRateLimit(clientIp, RateLimitService.RateLimitType.LOGIN);
        }

        // Should have 7 remaining
        remaining = rateLimitService.getRemainingRequests(clientIp, RateLimitService.RateLimitType.LOGIN);
        assertThat(remaining).isEqualTo(7);
    }
}
