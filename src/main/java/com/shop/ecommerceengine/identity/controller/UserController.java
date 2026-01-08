package com.shop.ecommerceengine.identity.controller;

import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.dto.UserDTO;
import com.shop.ecommerceengine.identity.dto.UserUpdateDTO;
import com.shop.ecommerceengine.identity.exception.AuthException;
import com.shop.ecommerceengine.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for user profile management.
 * Users can view and update their own profiles.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User profile management endpoints")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get user by ID.
     * Users can only view their own profile or admins can view any.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user by ID",
               description = "Retrieves user information. Users can only view their own profile.")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(
            @PathVariable UUID id,
            Authentication authentication) {

        // Check if user is viewing their own profile or is admin
        String currentUsername = authentication.getName();
        UserDTO user = userService.getUserById(id);

        // Regular users can only view their own profile
        if (!user.username().equals(currentUsername) &&
                authentication.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().startsWith("ROLE_ADMIN") ||
                                a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
            throw new AuthException("You can only view your own profile", "ACCESS_DENIED");
        }

        return ResponseEntity.ok(ApiResponse.success(user, "User retrieved successfully"));
    }

    /**
     * Update user profile.
     * Users can only update their own profile.
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update user profile",
               description = "Updates user profile. Users can only update their own profile.")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateDTO request,
            Authentication authentication) {

        log.debug("Update user request for id: {}", id);

        // Verify user is updating their own profile
        String currentUsername = authentication.getName();
        UserDTO existingUser = userService.getUserById(id);

        if (!existingUser.username().equals(currentUsername)) {
            throw new AuthException("You can only update your own profile", "ACCESS_DENIED");
        }

        UserDTO updatedUser = userService.updateUser(id, request);

        return ResponseEntity.ok(ApiResponse.success(updatedUser, "Profile updated successfully"));
    }
}
