package com.shop.ecommerceengine.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending emails asynchronously.
 * Uses Spring Mail with @Async for non-blocking operations.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@ecommerce-engine.com}")
    private String fromAddress;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send email verification link asynchronously.
     */
    @Async
    public void sendEmailVerification(String to, String username, String token) {
        if (!mailEnabled) {
            log.info("Email disabled. Verification link for {}: {}/api/v1/auth/verify-email?token={}",
                    username, baseUrl, token);
            return;
        }

        try {
            String verificationLink = baseUrl + "/api/v1/auth/verify-email?token=" + token;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject("Verify your email - E-Commerce Engine");
            message.setText(String.format("""
                    Hello %s,

                    Thank you for registering with E-Commerce Engine!

                    Please verify your email by clicking the link below:
                    %s

                    This link will expire in 24 hours.

                    If you did not create an account, please ignore this email.

                    Best regards,
                    E-Commerce Engine Team
                    """, username, verificationLink));

            mailSender.send(message);
            log.info("Verification email sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Send password reset link asynchronously.
     */
    @Async
    public void sendPasswordReset(String to, String username, String token) {
        if (!mailEnabled) {
            log.info("Email disabled. Password reset link for {}: {}/reset-password?token={}",
                    username, baseUrl, token);
            return;
        }

        try {
            String resetLink = baseUrl + "/reset-password?token=" + token;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject("Password Reset Request - E-Commerce Engine");
            message.setText(String.format("""
                    Hello %s,

                    We received a request to reset your password.

                    Click the link below to reset your password:
                    %s

                    This link will expire in 1 hour.

                    If you did not request a password reset, please ignore this email.
                    Your password will remain unchanged.

                    Best regards,
                    E-Commerce Engine Team
                    """, username, resetLink));

            mailSender.send(message);
            log.info("Password reset email sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Send password change confirmation asynchronously.
     */
    @Async
    public void sendPasswordChangeConfirmation(String to, String username) {
        if (!mailEnabled) {
            log.info("Email disabled. Password change confirmation for: {}", username);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject("Password Changed Successfully - E-Commerce Engine");
            message.setText(String.format("""
                    Hello %s,

                    Your password has been successfully changed.

                    If you did not make this change, please contact our support team immediately.

                    Best regards,
                    E-Commerce Engine Team
                    """, username));

            mailSender.send(message);
            log.info("Password change confirmation sent to: {}", to);

        } catch (Exception e) {
            log.error("Failed to send password change confirmation to {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Check if email is enabled.
     */
    public boolean isEmailEnabled() {
        return mailEnabled;
    }
}
