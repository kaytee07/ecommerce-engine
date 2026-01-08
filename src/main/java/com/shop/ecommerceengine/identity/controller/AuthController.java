package com.shop.ecommerceengine.identity.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.common.service.RateLimitService;
import com.shop.ecommerceengine.identity.dto.*;
import com.shop.ecommerceengine.identity.exception.AuthException;
import com.shop.ecommerceengine.identity.service.AuthService;
import com.shop.ecommerceengine.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Arrays;

/**
 * Authentication controller handling login, logout, token refresh, user info,
 * registration, and password recovery.
 *
 * Endpoints:
 * - POST /api/v1/auth/login: First-party login (returns access token, refresh in cookie)
 * - POST /api/v1/auth/refresh: Refresh tokens (reads refresh from cookie)
 * - POST /api/v1/auth/logout: Logout (blacklists tokens)
 * - GET /api/v1/auth/me: Get current user info
 * - POST /api/v1/auth/register: Register new user
 * - GET /api/v1/auth/verify-email: Verify email address
 * - POST /api/v1/auth/forgot-password: Request password reset
 * - POST /api/v1/auth/reset-password: Reset password with token
 * - POST /oauth2/token: OAuth2 token endpoint (client credentials, refresh)
 */
@RestController
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;
    private final UserService userService;
    private final RateLimitService rateLimitService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${jwt.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${jwt.cookie.same-site:Strict}")
    private String sameSite;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public AuthController(AuthService authService, UserService userService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.userService = userService;
        this.rateLimitService = rateLimitService;
    }

    /**
     * First-party login endpoint.
     * Returns access token in response body, refresh token in HttpOnly cookie.
     * Rate limited: 10 requests per minute per IP.
     */
    @PostMapping("/api/v1/auth/login")
    @Operation(summary = "Login with username and password",
               description = "Authenticates user and returns access token. Refresh token is set as HttpOnly cookie. Rate limited.")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        // Apply rate limiting
        rateLimitService.checkRateLimit(getClientIp(httpRequest), RateLimitService.RateLimitType.LOGIN);

        log.debug("Login attempt for user: {}", request.username());

        AuthService.AuthResult result = authService.login(request.username(), request.password());

        // Set refresh token as HttpOnly cookie
        setRefreshTokenCookie(response, result.refreshToken());

        return ResponseEntity.ok(ApiResponse.success(result.tokenResponse(), "Login successful"));
    }

    /**
     * Token refresh endpoint.
     * Reads refresh token from HttpOnly cookie, returns new access token.
     * Implements refresh token rotation for security.
     */
    @PostMapping("/api/v1/auth/refresh")
    @Operation(summary = "Refresh access token",
               description = "Uses refresh token from HttpOnly cookie to issue new access token with rotation.")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            throw new AuthException("Refresh token not found", AuthException.INVALID_TOKEN);
        }

        AuthService.AuthResult result = authService.refresh(refreshToken);

        // Set new rotated refresh token in cookie
        setRefreshTokenCookie(response, result.refreshToken());

        return ResponseEntity.ok(ApiResponse.success(result.tokenResponse(), "Token refreshed successfully"));
    }

    /**
     * Logout endpoint.
     * Blacklists access token and revokes all refresh tokens.
     */
    @PostMapping("/api/v1/auth/logout")
    @Operation(summary = "Logout",
               description = "Invalidates access token and revokes all refresh tokens for the user.")
    public ResponseEntity<ApiResponse<LogoutResponse>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        String accessToken = extractAccessToken(request);
        String refreshToken = extractRefreshTokenFromCookie(request);

        authService.logout(accessToken, refreshToken);

        // Clear refresh token cookie
        clearRefreshTokenCookie(response);

        return ResponseEntity.ok(ApiResponse.success(LogoutResponse.successful(), "Logged out successfully"));
    }

    /**
     * Get current authenticated user info.
     */
    @GetMapping("/api/v1/auth/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user",
               description = "Returns information about the currently authenticated user.")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getCurrentUser() {
        UserInfoResponse userInfo = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(userInfo, "User info retrieved"));
    }

    /**
     * User registration endpoint.
     * Creates new user with default ROLE_USER and sends verification email.
     * Rate limited: 5 requests per minute per IP.
     */
    @PostMapping("/api/v1/auth/register")
    @Operation(summary = "Register new user",
               description = "Creates a new user account and sends verification email if enabled. Rate limited.")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
            @Valid @RequestBody UserCreateDTO request,
            HttpServletRequest httpRequest) {

        // Apply rate limiting
        rateLimitService.checkRateLimit(getClientIp(httpRequest), RateLimitService.RateLimitType.REGISTER);

        log.debug("Registration attempt for username: {}", request.username());

        RegistrationResponse result = userService.registerUser(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, result.message()));
    }

    /**
     * Email verification endpoint.
     * Verifies user's email address and redirects to login.
     */
    @GetMapping("/api/v1/auth/verify-email")
    @Operation(summary = "Verify email address",
               description = "Verifies user's email address using the token from verification link.")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        log.debug("Email verification attempt with token");

        userService.verifyEmail(token);

        // Redirect to login page after successful verification
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(baseUrl + "/login?verified=true"))
                .body(ApiResponse.success(null, "Email verified successfully. Please login."));
    }

    /**
     * Forgot password endpoint.
     * Sends password reset email if user exists.
     * Returns success regardless to prevent user enumeration.
     * Rate limited: 3 requests per 5 minutes per IP.
     */
    @PostMapping("/api/v1/auth/forgot-password")
    @Operation(summary = "Request password reset",
               description = "Sends password reset link to email if account exists. " +
                       "Always returns success to prevent user enumeration. Rate limited.")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        // Apply rate limiting - stricter for password reset
        rateLimitService.checkRateLimit(getClientIp(httpRequest), RateLimitService.RateLimitType.PASSWORD_RESET);

        log.debug("Password reset request for email: {}", request.email());

        userService.requestPasswordReset(request.email());

        // Always return success to prevent email enumeration
        return ResponseEntity.ok(ApiResponse.success(null,
                "If an account exists with this email, a password reset link has been sent."));
    }

    /**
     * Reset password endpoint.
     * Resets password using token from email link.
     * Rate limited: 3 requests per 5 minutes per IP.
     */
    @PostMapping("/api/v1/auth/reset-password")
    @Operation(summary = "Reset password",
               description = "Resets user's password using the token from reset email. Rate limited.")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        // Apply rate limiting - stricter for password reset
        rateLimitService.checkRateLimit(getClientIp(httpRequest), RateLimitService.RateLimitType.PASSWORD_RESET);

        log.debug("Password reset attempt with token");

        userService.resetPassword(request);

        return ResponseEntity.ok(ApiResponse.success(null,
                "Password reset successfully. Please login with your new password."));
    }

    /**
     * OAuth2 token endpoint for client credentials and refresh token grants.
     * Note: Authorization Code + PKCE flow redirects handled separately.
     */
    @PostMapping("/oauth2/token")
    @Operation(summary = "OAuth2 Token Endpoint",
               description = "Issues tokens for client_credentials grant or refreshes tokens.")
    public ResponseEntity<?> oauth2Token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "scope", required = false) String scope,
            HttpServletRequest request,
            HttpServletResponse response) {

        return switch (grantType) {
            case OAuth2TokenRequest.GRANT_CLIENT_CREDENTIALS -> handleClientCredentials(clientId, clientSecret, scope);
            case OAuth2TokenRequest.GRANT_REFRESH_TOKEN -> handleRefreshGrant(request, response);
            case "password" -> ResponseEntity.badRequest().body(
                    new OAuth2ErrorResponse(
                            OAuth2ErrorResponse.UNSUPPORTED_GRANT_TYPE,
                            "Password grant is not supported. Use Authorization Code + PKCE flow."
                    )
            );
            default -> ResponseEntity.badRequest().body(
                    new OAuth2ErrorResponse(
                            OAuth2ErrorResponse.UNSUPPORTED_GRANT_TYPE,
                            "Unsupported grant type: " + grantType
                    )
            );
        };
    }

    /**
     * Handle client credentials grant (machine-to-machine auth).
     * Note: In a full implementation, this would validate client credentials against a client registry.
     */
    private ResponseEntity<?> handleClientCredentials(String clientId, String clientSecret, String scope) {
        // TODO: Implement client credentials validation against registered clients
        // For now, return an error indicating this needs setup
        log.warn("Client credentials grant attempted but not fully implemented");

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(
                new OAuth2ErrorResponse(
                        OAuth2ErrorResponse.INVALID_CLIENT,
                        "Client credentials grant requires client registration. Coming in Phase 3."
                )
        );
    }

    /**
     * Handle refresh token grant via OAuth2 endpoint.
     */
    private ResponseEntity<?> handleRefreshGrant(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshTokenFromCookie(request);
        if (refreshToken == null) {
            return ResponseEntity.badRequest().body(
                    new OAuth2ErrorResponse(
                            OAuth2ErrorResponse.INVALID_REQUEST,
                            "Refresh token not found in cookie"
                    )
            );
        }

        try {
            AuthService.AuthResult result = authService.refresh(refreshToken);
            setRefreshTokenCookie(response, result.refreshToken());

            return ResponseEntity.ok(result.tokenResponse());
        } catch (AuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new OAuth2ErrorResponse(OAuth2ErrorResponse.INVALID_GRANT, e.getMessage())
            );
        }
    }

    /**
     * Extract refresh token from HttpOnly cookie.
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> REFRESH_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extract access token from Authorization header.
     */
    private String extractAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Set refresh token as HttpOnly, Secure, SameSite cookie.
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath(contextPath.isEmpty() ? "/" : contextPath);
        cookie.setMaxAge((int) authService.getRefreshTokenExpirationSeconds());
        // Note: SameSite attribute needs to be set via header in older servlet specs
        response.addCookie(cookie);

        // Add SameSite via header for better browser support
        String cookieHeader = String.format(
                "%s=%s; HttpOnly; %sPath=%s; Max-Age=%d; SameSite=%s",
                REFRESH_TOKEN_COOKIE,
                refreshToken,
                secureCookie ? "Secure; " : "",
                contextPath.isEmpty() ? "/" : contextPath,
                (int) authService.getRefreshTokenExpirationSeconds(),
                sameSite
        );
        response.addHeader("Set-Cookie", cookieHeader);
    }

    /**
     * Clear refresh token cookie on logout.
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setPath(contextPath.isEmpty() ? "/" : contextPath);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * Extract client IP address from request.
     * Handles X-Forwarded-For header for requests behind proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one (client IP)
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
