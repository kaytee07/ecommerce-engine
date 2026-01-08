package com.shop.ecommerceengine.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for user profile updates.
 * All fields are optional - only non-null fields are updated.
 */
public record UserUpdateDTO(
        @Email(message = "Invalid email format")
        String email,

        @Size(max = 100, message = "Full name cannot exceed 100 characters")
        String fullName,

        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
        String currentPassword,

        @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                message = "New password must contain at least one uppercase letter, one lowercase letter, and one digit")
        String newPassword
) {}
