package com.shop.ecommerceengine.identity.filter;

import com.shop.ecommerceengine.identity.exception.AuthException;
import com.shop.ecommerceengine.identity.service.JwtTokenService;
import com.shop.ecommerceengine.identity.service.RefreshTokenService;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * JWT Authentication Filter.
 * Extracts JWT from Authorization header, validates it, and sets authentication.
 *
 * Features:
 * - Extracts Bearer token from Authorization header
 * - Validates token signature and expiry
 * - Checks token blacklist (for logout support)
 * - Sets SecurityContext with user principal and authorities
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   RefreshTokenService refreshTokenService) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostConstruct
    public void init() {
        log.debug("JWT Authentication Filter initialized");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractTokenFromRequest(request);

            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticateWithToken(token, request);
            }
        } catch (AuthException e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            // Don't set authentication - let the request proceed to trigger 401 if endpoint requires auth
        } catch (Exception e) {
            log.warn("Unexpected error during JWT authentication: {}", e.getMessage());
            // Don't set authentication
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract Bearer token from Authorization header.
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /**
     * Validate token and set authentication context.
     */
    private void authenticateWithToken(String token, HttpServletRequest request) {
        // Validate and parse token
        Claims claims = jwtTokenService.validateAndParseToken(token);

        String tokenId = claims.getId();
        String username = claims.getSubject();

        // Check if token is blacklisted (logout)
        if (refreshTokenService.isAccessTokenBlacklisted(tokenId)) {
            log.debug("Token is blacklisted: {}", tokenId);
            throw new AuthException("Token has been revoked", AuthException.TOKEN_REVOKED);
        }

        // Extract roles from claims
        Set<String> roles = jwtTokenService.getRoles(token);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        // Create authentication token
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Set authentication in context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated user: {} with roles: {}", username, roles);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip filter for public endpoints
        return path.startsWith("/api/v1/auth/login") ||
               path.startsWith("/api/v1/auth/register") ||
               path.startsWith("/api/v1/auth/forgot-password") ||
               path.startsWith("/api/v1/auth/reset-password") ||
               path.startsWith("/api/v1/auth/verify-email") ||
               path.startsWith("/oauth2/") ||
               path.startsWith("/api/health") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/error");
    }
}
