package com.shop.ecommerceengine.identity.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ecommerceengine.common.dto.ApiError;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;

/**
 * Security configuration for the application.
 * Configures stateless JWT authentication with OAuth2 resource server support.
 *
 * Authentication flows:
 * - First-party login: POST /api/v1/auth/login with username/password
 * - OAuth2: Authorization Code + PKCE via /oauth2/authorize, /oauth2/token
 * - Client Credentials: POST /oauth2/token with client_id/secret
 *
 * All endpoints require authentication except public ones.
 * Refresh tokens are HttpOnly cookies, not in response body.
 *
 * CSRF Protection Strategy:
 * - CSRF is disabled for stateless JWT Bearer token authentication (standard practice)
 * - For cookie-based refresh tokens, protection is provided by:
 *   1. SameSite=Strict cookie attribute (prevents cross-site cookie submission)
 *   2. HttpOnly flag (prevents XSS from accessing token)
 *   3. Secure flag in production (HTTPS only)
 *   4. Short refresh token TTL with rotation
 * - This multi-layered approach provides equivalent or better protection than traditional CSRF tokens
 *
 * Rate Limiting:
 * - Auth endpoints are rate-limited via RateLimitService to prevent brute force attacks
 * - See AuthController for specific limits per endpoint
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless API (JWT handles this)
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session management
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no authentication required
                        .requestMatchers(
                                // Auth endpoints
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/register",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email",
                                // OAuth2 endpoints
                                "/oauth2/authorize",
                                "/oauth2/token",
                                // Storefront endpoints (public catalog browsing)
                                "/api/v1/store/**",
                                // Health and docs
                                "/api/health",
                                "/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/error"
                        ).permitAll()

                        // Admin endpoints require specific roles
                        .requestMatchers("/api/v1/admin/**").hasAnyRole(
                                "SUPER_ADMIN",
                                "ADMIN",
                                "CONTENT_MANAGER",
                                "SUPPORT_AGENT",
                                "WAREHOUSE"
                        )

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Custom exception handling for auth errors
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                            ApiError apiError = new ApiError(
                                    HttpServletResponse.SC_UNAUTHORIZED,
                                    "Authentication required",
                                    "UNAUTHORIZED"
                            );
                            apiError.setTraceId(MDC.get("traceId"));
                            apiError.setTimestamp(LocalDateTime.now());

                            ApiResponse<ApiError> apiResponse = ApiResponse.error(apiError, "Authentication required");
                            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

                            ApiError apiError = new ApiError(
                                    HttpServletResponse.SC_FORBIDDEN,
                                    "Access denied - insufficient permissions",
                                    "ACCESS_DENIED"
                            );
                            apiError.setTraceId(MDC.get("traceId"));
                            apiError.setTimestamp(LocalDateTime.now());

                            ApiResponse<ApiError> apiResponse = ApiResponse.error(apiError, "Access denied");
                            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                        })
                )

                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService,
                                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider) {
        return new ProviderManager(daoAuthenticationProvider);
    }
}
