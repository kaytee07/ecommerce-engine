package com.shop.ecommerceengine.common.service;

import com.shop.ecommerceengine.common.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Service for IP-based rate limiting using Redis.
 * Each endpoint type has its own configuration for limits and time windows.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Rate limit configurations for different endpoint types.
     */
    public enum RateLimitType {
        LOGIN(10, Duration.ofMinutes(1)),           // 10 requests per minute
        REGISTER(5, Duration.ofMinutes(1)),         // 5 requests per minute
        PASSWORD_RESET(3, Duration.ofMinutes(5)),   // 3 requests per 5 minutes
        GENERAL_AUTH(20, Duration.ofMinutes(1));    // 20 requests per minute

        private final int maxRequests;
        private final Duration window;

        RateLimitType(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public Duration getWindow() {
            return window;
        }
    }

    /**
     * Check if the request is allowed based on rate limit.
     * Uses Redis INCR with expiry for atomic counter increment.
     *
     * @param clientIdentifier IP address or other client identifier
     * @param limitType        Type of rate limit to apply
     * @throws RateLimitExceededException if rate limit is exceeded
     */
    public void checkRateLimit(String clientIdentifier, RateLimitType limitType) {
        String key = buildKey(clientIdentifier, limitType);

        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount == null) {
                log.warn("Redis returned null for rate limit key: {}", key);
                return; // Allow request if Redis is unavailable
            }

            // Set expiry only on first request (when count is 1)
            if (currentCount == 1) {
                redisTemplate.expire(key, limitType.getWindow().getSeconds(), TimeUnit.SECONDS);
            }

            if (currentCount > limitType.getMaxRequests()) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                long retryAfter = ttl != null && ttl > 0 ? ttl : limitType.getWindow().getSeconds();

                log.warn("Rate limit exceeded for {} on {}: {} requests (max: {})",
                        clientIdentifier, limitType.name(), currentCount, limitType.getMaxRequests());

                throw new RateLimitExceededException(retryAfter);
            }

            log.debug("Rate limit check passed for {} on {}: {}/{} requests",
                    clientIdentifier, limitType.name(), currentCount, limitType.getMaxRequests());

        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            // Log error but allow request if Redis is unavailable
            log.error("Error checking rate limit for {}: {}", clientIdentifier, e.getMessage());
        }
    }

    /**
     * Get remaining requests for a client.
     *
     * @param clientIdentifier IP address or other client identifier
     * @param limitType        Type of rate limit
     * @return Remaining requests, or -1 if unable to determine
     */
    public int getRemainingRequests(String clientIdentifier, RateLimitType limitType) {
        String key = buildKey(clientIdentifier, limitType);

        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return limitType.getMaxRequests();
            }
            int currentCount = Integer.parseInt(value);
            return Math.max(0, limitType.getMaxRequests() - currentCount);
        } catch (Exception e) {
            log.error("Error getting remaining requests: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * Reset rate limit for a client (useful for testing or admin override).
     *
     * @param clientIdentifier IP address or other client identifier
     * @param limitType        Type of rate limit
     */
    public void resetRateLimit(String clientIdentifier, RateLimitType limitType) {
        String key = buildKey(clientIdentifier, limitType);
        redisTemplate.delete(key);
        log.info("Rate limit reset for {} on {}", clientIdentifier, limitType.name());
    }

    private String buildKey(String clientIdentifier, RateLimitType limitType) {
        return RATE_LIMIT_KEY_PREFIX + limitType.name().toLowerCase() + ":" + clientIdentifier;
    }
}
