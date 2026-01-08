package com.shop.ecommerceengine.identity.service;

import com.shop.ecommerceengine.identity.exception.InvalidTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing verification and password reset tokens using Redis.
 * Tokens are stored with TTL for automatic expiration.
 */
@Service
public class VerificationTokenService {

    private static final Logger log = LoggerFactory.getLogger(VerificationTokenService.class);

    private static final String EMAIL_VERIFICATION_PREFIX = "email-verification:";
    private static final String PASSWORD_RESET_PREFIX = "password-reset:";
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public VerificationTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generate and store an email verification token.
     *
     * @param username The username to associate with the token
     * @return The generated token
     */
    public String generateEmailVerificationToken(String username) {
        String token = UUID.randomUUID().toString();
        String key = EMAIL_VERIFICATION_PREFIX + token;

        redisTemplate.opsForValue().set(key, username, EMAIL_VERIFICATION_TTL);
        log.debug("Generated email verification token for user: {}", username);

        return token;
    }

    /**
     * Validate an email verification token.
     *
     * @param token The token to validate
     * @return The username if valid
     * @throws InvalidTokenException if token is invalid or expired
     */
    public String validateEmailVerificationToken(String token) {
        String key = EMAIL_VERIFICATION_PREFIX + token;
        String username = redisTemplate.opsForValue().get(key);

        if (username == null) {
            log.warn("Invalid or expired email verification token");
            throw new InvalidTokenException("Invalid or expired verification token");
        }

        return username;
    }

    /**
     * Delete an email verification token after use.
     *
     * @param token The token to delete
     */
    public void deleteEmailVerificationToken(String token) {
        String key = EMAIL_VERIFICATION_PREFIX + token;
        redisTemplate.delete(key);
        log.debug("Deleted email verification token");
    }

    /**
     * Generate and store a password reset token.
     *
     * @param username The username to associate with the token
     * @return The generated token
     */
    public String generatePasswordResetToken(String username) {
        // First, invalidate any existing password reset tokens for this user
        // This prevents multiple valid reset tokens
        invalidateExistingPasswordResetTokens(username);

        String token = UUID.randomUUID().toString();
        String key = PASSWORD_RESET_PREFIX + token;

        // Store token -> username mapping
        redisTemplate.opsForValue().set(key, username, PASSWORD_RESET_TTL);

        // Store username -> token mapping for invalidation
        String userKey = PASSWORD_RESET_PREFIX + "user:" + username;
        redisTemplate.opsForValue().set(userKey, token, PASSWORD_RESET_TTL);

        log.debug("Generated password reset token for user: {}", username);

        return token;
    }

    /**
     * Validate a password reset token.
     *
     * @param token The token to validate
     * @return The username if valid
     * @throws InvalidTokenException if token is invalid or expired
     */
    public String validatePasswordResetToken(String token) {
        String key = PASSWORD_RESET_PREFIX + token;
        String username = redisTemplate.opsForValue().get(key);

        if (username == null) {
            log.warn("Invalid or expired password reset token");
            throw new InvalidTokenException("Invalid or expired password reset token");
        }

        return username;
    }

    /**
     * Delete a password reset token after use.
     *
     * @param token The token to delete
     */
    public void deletePasswordResetToken(String token) {
        String key = PASSWORD_RESET_PREFIX + token;
        String username = redisTemplate.opsForValue().get(key);

        redisTemplate.delete(key);

        if (username != null) {
            String userKey = PASSWORD_RESET_PREFIX + "user:" + username;
            redisTemplate.delete(userKey);
        }

        log.debug("Deleted password reset token");
    }

    /**
     * Invalidate any existing password reset tokens for a user.
     */
    private void invalidateExistingPasswordResetTokens(String username) {
        String userKey = PASSWORD_RESET_PREFIX + "user:" + username;
        String existingToken = redisTemplate.opsForValue().get(userKey);

        if (existingToken != null) {
            String tokenKey = PASSWORD_RESET_PREFIX + existingToken;
            redisTemplate.delete(tokenKey);
            redisTemplate.delete(userKey);
            log.debug("Invalidated existing password reset token for user: {}", username);
        }
    }

    /**
     * Check if a token exists (for testing purposes).
     */
    public boolean tokenExists(String prefix, String token) {
        String key = prefix + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get the remaining TTL for a token.
     */
    public Optional<Long> getTokenTTL(String prefix, String token) {
        String key = prefix + token;
        Long ttl = redisTemplate.getExpire(key);
        return ttl != null && ttl > 0 ? Optional.of(ttl) : Optional.empty();
    }
}
