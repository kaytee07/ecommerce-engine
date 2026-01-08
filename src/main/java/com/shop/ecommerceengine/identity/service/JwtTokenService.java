package com.shop.ecommerceengine.identity.service;

import com.shop.ecommerceengine.identity.exception.InvalidTokenException;
import com.shop.ecommerceengine.identity.exception.TokenExpiredException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Service for JWT token generation and validation.
 * Handles access tokens only - refresh tokens are managed by RefreshTokenService.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token.expiration:3600000}") // 1 hour default
    private long accessTokenExpiration;

    @Value("${jwt.issuer:ecommerce-engine}")
    private String issuer;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters. Configure jwt.secret in application.yml or .env");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT Token Service initialized with access token expiration: {}ms", accessTokenExpiration);
    }

    /**
     * Generate a new access token for the given user.
     *
     * @param username The username (subject)
     * @param roles    The user's roles
     * @return The generated JWT access token
     */
    public String generateAccessToken(String username, Set<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiration);

        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(tokenId)
                .subject(username)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .signWith(secretKey)
                .compact();

        log.debug("Generated access token for user: {} with jti: {}", username, tokenId);
        return token;
    }

    /**
     * Validate and parse a JWT access token.
     *
     * @param token The JWT token string
     * @return The parsed claims
     * @throws InvalidTokenException if the token is invalid
     * @throws TokenExpiredException if the token has expired
     */
    public Claims validateAndParseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token expired for subject: {}", e.getClaims().getSubject());
            throw new TokenExpiredException("Access token has expired");
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            throw new InvalidTokenException("Malformed token");
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token signature");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
            throw new InvalidTokenException("Unsupported token format");
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null");
            throw new InvalidTokenException("Token is required");
        }
    }

    /**
     * Extract the username (subject) from a token.
     */
    public String getUsername(String token) {
        return validateAndParseToken(token).getSubject();
    }

    /**
     * Extract the token ID (jti) from a token.
     */
    public String getTokenId(String token) {
        return validateAndParseToken(token).getId();
    }

    /**
     * Extract roles from a token.
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRoles(String token) {
        Claims claims = validateAndParseToken(token);
        Object rolesObj = claims.get(CLAIM_ROLES);
        if (rolesObj instanceof java.util.Collection<?> collection) {
            return Set.copyOf(collection.stream()
                    .map(Object::toString)
                    .toList());
        }
        return Set.of();
    }

    /**
     * Get the expiration time remaining in seconds.
     */
    public long getExpirationInSeconds(String token) {
        Claims claims = validateAndParseToken(token);
        Date expiration = claims.getExpiration();
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        return Math.max(0, remainingMs / 1000);
    }

    /**
     * Get the configured access token expiration in seconds.
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    /**
     * Check if a token is an access token.
     */
    public boolean isAccessToken(String token) {
        Claims claims = validateAndParseToken(token);
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }
}
