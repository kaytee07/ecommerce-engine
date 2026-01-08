package com.shop.ecommerceengine.identity.service;

import com.shop.ecommerceengine.identity.exception.InvalidTokenException;
import com.shop.ecommerceengine.identity.exception.TokenExpiredException;
import com.shop.ecommerceengine.identity.exception.TokenRevokedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing refresh tokens with Redis storage.
 * Implements token rotation and replay attack protection.
 *
 * Storage strategy:
 * - refresh_token:{tokenId} -> username (with TTL)
 * - refresh_family:{familyId} -> current tokenId (for rotation tracking)
 * - user_refresh:{username} -> Set of familyIds (for logout/revoke all)
 * - blacklist:access:{jti} -> "1" (for access token blacklisting on logout)
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final String KEY_PREFIX_REFRESH = "refresh_token:";
    private static final String KEY_PREFIX_FAMILY = "refresh_family:";
    private static final String KEY_PREFIX_USER_TOKENS = "user_refresh:";
    private static final String KEY_PREFIX_BLACKLIST = "blacklist:access:";
    private static final String SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom;

    @Value("${jwt.refresh-token.expiration:604800000}") // 7 days default
    private long refreshTokenExpiration;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generate a new refresh token for a user.
     * Creates a new token family for tracking rotation.
     *
     * @param username The username
     * @return The refresh token string
     */
    public String generateRefreshToken(String username) {
        String familyId = generateSecureId();
        String tokenId = generateSecureId();

        // Store token -> username mapping
        String tokenKey = KEY_PREFIX_REFRESH + tokenId;
        String tokenValue = username + SEPARATOR + familyId;
        redisTemplate.opsForValue().set(tokenKey, tokenValue, Duration.ofMillis(refreshTokenExpiration));

        // Store family -> current token mapping
        String familyKey = KEY_PREFIX_FAMILY + familyId;
        redisTemplate.opsForValue().set(familyKey, tokenId, Duration.ofMillis(refreshTokenExpiration));

        // Track token family for user
        String userKey = KEY_PREFIX_USER_TOKENS + username;
        redisTemplate.opsForSet().add(userKey, familyId);
        redisTemplate.expire(userKey, Duration.ofMillis(refreshTokenExpiration * 2)); // Keep longer for cleanup

        log.debug("Generated refresh token for user: {}, family: {}", username, familyId);
        return tokenId;
    }

    /**
     * Validate and rotate a refresh token.
     * Implements refresh token rotation with replay attack detection.
     *
     * @param tokenId The refresh token ID
     * @return A record containing the username and new token ID
     * @throws InvalidTokenException if token doesn't exist
     * @throws TokenExpiredException if token has expired
     * @throws TokenRevokedException if replay attack detected
     */
    public RefreshResult rotateRefreshToken(String tokenId) {
        String tokenKey = KEY_PREFIX_REFRESH + tokenId;
        String tokenValue = redisTemplate.opsForValue().get(tokenKey);

        if (tokenValue == null) {
            log.warn("Refresh token not found: {}", tokenId);
            throw new InvalidTokenException("Refresh token not found or expired");
        }

        // Parse username and family from stored value
        String[] parts = tokenValue.split(SEPARATOR);
        if (parts.length != 2) {
            log.error("Invalid refresh token format in Redis");
            throw new InvalidTokenException("Invalid token format");
        }

        String username = parts[0];
        String familyId = parts[1];

        // Check if this is the current token in the family (replay detection)
        String familyKey = KEY_PREFIX_FAMILY + familyId;
        String currentTokenId = redisTemplate.opsForValue().get(familyKey);

        if (currentTokenId == null) {
            // Family doesn't exist - token was already rotated and expired
            log.warn("Token family not found - possible stale token");
            throw new TokenExpiredException("Refresh token has expired");
        }

        if (!tokenId.equals(currentTokenId)) {
            // REPLAY ATTACK DETECTED!
            // Someone is reusing an old refresh token - revoke entire family
            log.error("REPLAY ATTACK DETECTED for user: {}, family: {}", username, familyId);
            revokeTokenFamily(username, familyId);
            throw TokenRevokedException.replayAttackDetected();
        }

        // Token is valid - rotate it
        // Delete old token
        redisTemplate.delete(tokenKey);

        // Generate new token in same family
        String newTokenId = generateSecureId();
        String newTokenKey = KEY_PREFIX_REFRESH + newTokenId;
        String newTokenValue = username + SEPARATOR + familyId;

        redisTemplate.opsForValue().set(newTokenKey, newTokenValue, Duration.ofMillis(refreshTokenExpiration));
        redisTemplate.opsForValue().set(familyKey, newTokenId, Duration.ofMillis(refreshTokenExpiration));

        log.debug("Rotated refresh token for user: {}, old: {}, new: {}", username, tokenId, newTokenId);
        return new RefreshResult(username, newTokenId);
    }

    /**
     * Revoke all tokens for a user (used on logout).
     *
     * @param username The username
     */
    public void revokeAllUserTokens(String username) {
        String userKey = KEY_PREFIX_USER_TOKENS + username;
        Set<String> familyIds = redisTemplate.opsForSet().members(userKey);

        if (familyIds != null) {
            for (String familyId : familyIds) {
                revokeTokenFamily(username, familyId);
            }
        }

        redisTemplate.delete(userKey);
        log.info("Revoked all refresh tokens for user: {}", username);
    }

    /**
     * Revoke a specific token family.
     */
    private void revokeTokenFamily(String username, String familyId) {
        String familyKey = KEY_PREFIX_FAMILY + familyId;
        String tokenId = redisTemplate.opsForValue().get(familyKey);

        if (tokenId != null) {
            redisTemplate.delete(KEY_PREFIX_REFRESH + tokenId);
        }
        redisTemplate.delete(familyKey);

        // Remove from user's families
        String userKey = KEY_PREFIX_USER_TOKENS + username;
        redisTemplate.opsForSet().remove(userKey, familyId);

        log.debug("Revoked token family: {} for user: {}", familyId, username);
    }

    /**
     * Blacklist an access token (used on logout).
     * The token will be rejected until it expires.
     *
     * @param jti             The token ID (jti claim)
     * @param remainingTtlMs  Remaining TTL in milliseconds
     */
    public void blacklistAccessToken(String jti, long remainingTtlMs) {
        if (remainingTtlMs > 0) {
            String key = KEY_PREFIX_BLACKLIST + jti;
            redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(remainingTtlMs));
            log.debug("Blacklisted access token: {}", jti);
        }
    }

    /**
     * Check if an access token is blacklisted.
     *
     * @param jti The token ID (jti claim)
     * @return true if blacklisted
     */
    public boolean isAccessTokenBlacklisted(String jti) {
        String key = KEY_PREFIX_BLACKLIST + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get the refresh token expiration in seconds.
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpiration / 1000;
    }

    /**
     * Generate a cryptographically secure random ID.
     */
    private String generateSecureId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Result of refresh token validation and rotation.
     */
    public record RefreshResult(String username, String newTokenId) {}
}
