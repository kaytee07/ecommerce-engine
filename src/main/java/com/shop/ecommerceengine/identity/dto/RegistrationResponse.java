package com.shop.ecommerceengine.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for successful registration response.
 */
public record RegistrationResponse(
        UserDTO user,
        @JsonProperty("verification_email_sent")
        boolean verificationEmailSent,
        String message
) {
    public static RegistrationResponse success(UserDTO user, boolean emailSent) {
        String msg = emailSent
                ? "Registration successful. Please check your email to verify your account."
                : "Registration successful. Email verification is disabled.";
        return new RegistrationResponse(user, emailSent, msg);
    }
}
