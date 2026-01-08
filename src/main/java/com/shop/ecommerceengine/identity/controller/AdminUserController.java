package com.shop.ecommerceengine.identity.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.dto.RoleAssignDTO;
import com.shop.ecommerceengine.identity.dto.UserDTO;
import com.shop.ecommerceengine.identity.entity.UserEntity;
import com.shop.ecommerceengine.identity.exception.UserNotFoundException;
import com.shop.ecommerceengine.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin controller for user management.
 * Requires admin roles for all operations.
 * All actions are audit logged.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Users", description = "Admin endpoints for user management")
public class AdminUserController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserController.class);

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get all users with pagination.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'SUPPORT_AGENT')")
    @Operation(summary = "List all users",
               description = "Retrieves paginated list of all users. Requires admin or support role.")
    public ResponseEntity<ApiResponse<Page<UserDTO>>> getAllUsers(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<UserDTO> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(ApiResponse.success(users, "Users retrieved successfully"));
    }

    /**
     * Search users by username or email.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'SUPPORT_AGENT')")
    @Operation(summary = "Search users",
               description = "Searches users by username or email. Requires admin or support role.")
    public ResponseEntity<ApiResponse<Page<UserDTO>>> searchUsers(
            @RequestParam("q") String query,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<UserDTO> users = userService.searchUsers(query, pageable);
        return ResponseEntity.ok(ApiResponse.success(users, "Search results retrieved"));
    }

    /**
     * Get user by ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'SUPPORT_AGENT')")
    @Operation(summary = "Get user by ID",
               description = "Retrieves user details by ID. Requires admin or support role.")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable UUID id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user, "User retrieved successfully"));
    }

    /**
     * Assign roles to a user.
     * Only SUPER_ADMIN can assign roles.
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Assign roles to user",
               description = "Assigns roles to a user. Only SUPER_ADMIN can perform this action.")
    public ResponseEntity<ApiResponse<UserDTO>> assignRoles(
            @PathVariable UUID id,
            @Valid @RequestBody RoleAssignDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Role assignment request for user {} by {}", id, authentication.getName());

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        UserDTO user = userService.assignRoles(id, request.roles(), adminId, adminUsername,
                ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success(user, "Roles assigned successfully"));
    }

    /**
     * Enable a user account.
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Enable user account",
               description = "Enables a disabled user account. Requires SUPER_ADMIN or ADMIN role.")
    public ResponseEntity<ApiResponse<UserDTO>> enableUser(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Enable user request for {} by {}", id, authentication.getName());

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        UserDTO user = userService.setUserEnabled(id, true, adminId, adminUsername,
                ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success(user, "User enabled successfully"));
    }

    /**
     * Disable a user account.
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Disable user account",
               description = "Disables a user account. Requires SUPER_ADMIN or ADMIN role.")
    public ResponseEntity<ApiResponse<UserDTO>> disableUser(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Disable user request for {} by {}", id, authentication.getName());

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        UserDTO user = userService.setUserEnabled(id, false, adminId, adminUsername,
                ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success(user, "User disabled successfully"));
    }

    /**
     * Lock a user account.
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Lock user account",
               description = "Locks a user account preventing login. Requires SUPER_ADMIN or ADMIN role.")
    public ResponseEntity<ApiResponse<UserDTO>> lockUser(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Lock user request for {} by {}", id, authentication.getName());

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        UserDTO user = userService.setUserLocked(id, true, adminId, adminUsername,
                ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success(user, "User locked successfully"));
    }

    /**
     * Unlock a user account.
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(summary = "Unlock user account",
               description = "Unlocks a locked user account. Requires SUPER_ADMIN or ADMIN role.")
    public ResponseEntity<ApiResponse<UserDTO>> unlockUser(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Unlock user request for {} by {}", id, authentication.getName());

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        UserDTO user = userService.setUserLocked(id, false, adminId, adminUsername,
                ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success(user, "User unlocked successfully"));
    }

    /**
     * Delete a user.
     * Only SUPER_ADMIN can delete users.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete user",
               description = "Deletes a user account. Only SUPER_ADMIN can perform this action.")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Delete user request for {} by {}", id, authentication.getName());

        UUID adminId = getAdminId(authentication);
        String adminUsername = authentication.getName();
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        userService.deleteUser(id, adminId, adminUsername, ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success(null, "User deleted successfully"));
    }

    /**
     * Extract admin's user ID from authentication.
     */
    private UUID getAdminId(Authentication authentication) {
        String username = authentication.getName();
        return userService.findByUsername(username)
                .map(UserEntity::getId)
                .orElseThrow(() -> UserNotFoundException.byUsername(username));
    }

    /**
     * Get client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
