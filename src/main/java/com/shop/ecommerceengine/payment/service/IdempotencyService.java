package com.shop.ecommerceengine.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for handling idempotency key management using Redis.
 * Keys are stored with a 24-hour TTL.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final String KEY_PREFIX = "idempotency:payment:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Try to acquire an idempotency lock for the given key and order.
     * Returns true if this is the first request with this key,
     * false if the key was already used.
     */
    public boolean tryAcquire(String idempotencyKey, UUID orderId) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        String value = orderId.toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, value, TTL);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Acquired idempotency lock for key: {}", idempotencyKey);
            return true;
        }

        // Key exists - check if it's for the same order (retry case)
        String existingValue = redisTemplate.opsForValue().get(redisKey);
        boolean isSameOrder = value.equals(existingValue);

        if (isSameOrder) {
            log.debug("Idempotency key {} already used for same order {}", idempotencyKey, orderId);
        } else {
            log.warn("Idempotency key {} used for different order. Expected: {}, Found: {}",
                    idempotencyKey, orderId, existingValue);
        }

        return false;
    }

    /**
     * Get the order ID associated with an idempotency key.
     */
    public UUID getOrderIdForKey(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        String value = redisTemplate.opsForValue().get(redisKey);

        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID in idempotency key {}: {}", idempotencyKey, value);
            return null;
        }
    }

    /**
     * Check if an idempotency key exists.
     */
    public boolean exists(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    /**
     * Remove an idempotency key (e.g., after failure).
     */
    public void remove(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        redisTemplate.delete(redisKey);
        log.debug("Removed idempotency key: {}", idempotencyKey);
    }
}
