package com.shop.ecommerceengine.identity.service;

import com.shop.ecommerceengine.identity.dto.TokenResponse;
import com.shop.ecommerceengine.identity.dto.UserInfoResponse;
import com.shop.ecommerceengine.identity.exception.AuthException;
import com.shop.ecommerceengine.identity.exception.InvalidCredentialsException;
import com.shop.ecommerceengine.identity.exception.TokenRevokedException;
import com.shop.ecommerceengine.identity.mapper.AuthMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service orchestrating authentication flows.
 * Handles login, token refresh, logout, and user info retrieval.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthMapper authMapper;

    public AuthService(AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       JwtTokenService jwtTokenService,
                       RefreshTokenService refreshTokenService,
                       AuthMapper authMapper) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.authMapper = authMapper;
    }

    /**
     * Authenticate user and generate tokens.
     * Note: This is for first-party login flow. OAuth2 Authorization Code flow
     * will be the primary method for frontend clients.
     *
     * @param username The username
     * @param password The password
     * @return TokenResponse containing access token (refresh token set in cookie separately)
     */
    public AuthResult login(String username, String password) {
        log.debug("Attempting login for user: {}", username);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            Set<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

            // Generate tokens
            String accessToken = jwtTokenService.generateAccessToken(username, roles);
            String refreshToken = refreshTokenService.generateRefreshToken(username);

            log.info("User {} logged in successfully", username);

            TokenResponse tokenResponse = new TokenResponse(
                    accessToken,
                    jwtTokenService.getAccessTokenExpirationSeconds(),
                    String.join(" ", roles)
            );

            return new AuthResult(tokenResponse, refreshToken);

        } catch (BadCredentialsException e) {
            log.warn("Login failed for user {}: Invalid credentials", username);
            throw new InvalidCredentialsException();
        } catch (DisabledException e) {
            log.warn("Login failed for user {}: Account disabled", username);
            throw new AuthException("Account is disabled", AuthException.USER_DISABLED);
        } catch (LockedException e) {
            log.warn("Login failed for user {}: Account locked", username);
            throw new AuthException("Account is locked", AuthException.USER_LOCKED);
        }
    }

    /**
     * Refresh tokens using a valid refresh token.
     *
     * @param refreshTokenId The refresh token ID (from HttpOnly cookie)
     * @return New AuthResult with rotated tokens
     */
    public AuthResult refresh(String refreshTokenId) {
        log.debug("Attempting token refresh");

        RefreshTokenService.RefreshResult result = refreshTokenService.rotateRefreshToken(refreshTokenId);
        String username = result.username();

        // Load user details to get current roles
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        Set<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // Generate new access token
        String accessToken = jwtTokenService.generateAccessToken(username, roles);

        log.debug("Token refreshed successfully for user: {}", username);

        TokenResponse tokenResponse = new TokenResponse(
                accessToken,
                jwtTokenService.getAccessTokenExpirationSeconds(),
                String.join(" ", roles)
        );

        return new AuthResult(tokenResponse, result.newTokenId());
    }

    /**
     * Logout user - blacklist access token and revoke refresh tokens.
     *
     * @param accessToken    The current access token
     * @param refreshTokenId The refresh token ID (from cookie, may be null)
     */
    public void logout(String accessToken, String refreshTokenId) {
        try {
            String username = jwtTokenService.getUsername(accessToken);
            String jti = jwtTokenService.getTokenId(accessToken);
            long remainingTtl = jwtTokenService.getExpirationInSeconds(accessToken) * 1000;

            // Blacklist the access token
            refreshTokenService.blacklistAccessToken(jti, remainingTtl);

            // Revoke all refresh tokens for user
            refreshTokenService.revokeAllUserTokens(username);

            log.info("User {} logged out successfully", username);

        } catch (Exception e) {
            // Even if token parsing fails, try to revoke refresh token if provided
            log.warn("Error during logout: {}", e.getMessage());
            // Still consider logout successful - tokens will eventually expire
        }
    }

    /**
     * Get current authenticated user info.
     *
     * @return UserInfoResponse for the current user
     */
    public UserInfoResponse getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Not authenticated", AuthException.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return authMapper.toUserInfoResponse(userDetails);
        }

        // Handle string principal (from JWT)
        if (principal instanceof String username) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            return authMapper.toUserInfoResponse(userDetails);
        }

        throw new AuthException("Unable to determine current user", AuthException.UNAUTHORIZED);
    }

    /**
     * Check if an access token is blacklisted (for filter use).
     */
    public boolean isTokenBlacklisted(String jti) {
        return refreshTokenService.isAccessTokenBlacklisted(jti);
    }

    /**
     * Get refresh token expiration for cookie max-age.
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenService.getRefreshTokenExpirationSeconds();
    }

    /**
     * Result of authentication containing both token response and refresh token.
     * Refresh token is returned separately so controller can set it in HttpOnly cookie.
     */
    public record AuthResult(TokenResponse tokenResponse, String refreshToken) {}
}
