package com.shop.ecommerceengine.identity.service;

import com.shop.ecommerceengine.identity.dto.*;
import com.shop.ecommerceengine.identity.entity.UserEntity;
import com.shop.ecommerceengine.identity.exception.AuthException;
import com.shop.ecommerceengine.identity.exception.UserExistsException;
import com.shop.ecommerceengine.identity.exception.UserNotFoundException;
import com.shop.ecommerceengine.identity.mapper.UserMapper;
import com.shop.ecommerceengine.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for user management operations.
 * Handles registration, profile updates, password management, and role assignment.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final Set<String> VALID_ROLES = Set.of(
            "ROLE_USER",
            "ROLE_ADMIN",
            "ROLE_SUPER_ADMIN",
            "ROLE_CONTENT_MANAGER",
            "ROLE_SUPPORT_AGENT",
            "ROLE_WAREHOUSE"
    );

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationTokenService verificationTokenService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       VerificationTokenService verificationTokenService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.verificationTokenService = verificationTokenService;
        this.auditService = auditService;
    }

    /**
     * Register a new user.
     *
     * @param dto User registration data
     * @return RegistrationResponse with user info and email status
     */
    @Transactional
    public RegistrationResponse registerUser(UserCreateDTO dto) {
        log.debug("Registering new user: {}", dto.username());

        // Check for existing username
        if (userRepository.existsByUsernameIgnoreCase(dto.username())) {
            throw UserExistsException.usernameExists(dto.username());
        }

        // Check for existing email
        if (userRepository.existsByEmailIgnoreCase(dto.email())) {
            throw UserExistsException.emailExists(dto.email());
        }

        // Create user entity
        UserEntity user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRoles(new HashSet<>(Set.of("ROLE_USER")));
        user.setEmailVerified(false);
        user.setEnabled(true);
        user.setLocked(false);

        UserEntity savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());

        // Send verification email asynchronously
        boolean emailSent = false;
        if (emailService.isEmailEnabled()) {
            String token = verificationTokenService.generateEmailVerificationToken(savedUser.getUsername());
            emailService.sendEmailVerification(savedUser.getEmail(), savedUser.getUsername(), token);
            emailSent = true;
        }

        UserDTO userDto = userMapper.toDto(savedUser);
        return RegistrationResponse.success(userDto, emailSent);
    }

    /**
     * Verify user's email address.
     *
     * @param token Verification token
     */
    @Transactional
    public void verifyEmail(String token) {
        String username = verificationTokenService.validateEmailVerificationToken(token);

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> UserNotFoundException.byUsername(username));

        if (user.isEmailVerified()) {
            log.debug("Email already verified for user: {}", username);
            verificationTokenService.deleteEmailVerificationToken(token);
            return;
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        verificationTokenService.deleteEmailVerificationToken(token);
        log.info("Email verified for user: {}", username);
    }

    /**
     * Request password reset.
     * Returns true regardless of email existence to prevent enumeration.
     *
     * @param email User's email address
     */
    @Transactional
    public void requestPasswordReset(String email) {
        log.debug("Password reset requested for email: {}", email);

        // Always log the same message to prevent timing attacks
        Optional<UserEntity> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            String token = verificationTokenService.generatePasswordResetToken(user.getUsername());
            emailService.sendPasswordReset(user.getEmail(), user.getUsername(), token);
            log.info("Password reset email sent to: {}", email);
        } else {
            // Log but don't expose that user doesn't exist
            log.debug("Password reset requested for non-existent email: {}", email);
        }
    }

    /**
     * Reset password using token.
     *
     * @param dto Password reset request with token and new password
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest dto) {
        String username = verificationTokenService.validatePasswordResetToken(dto.token());

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> UserNotFoundException.byUsername(username));

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        userRepository.save(user);

        verificationTokenService.deletePasswordResetToken(dto.token());

        emailService.sendPasswordChangeConfirmation(user.getEmail(), user.getUsername());
        log.info("Password reset successfully for user: {}", username);
    }

    /**
     * Get user by ID.
     */
    public UserDTO getUserById(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> UserNotFoundException.byId(id));
        return userMapper.toDto(user);
    }

    /**
     * Get user by username.
     */
    public UserDTO getUserByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> UserNotFoundException.byUsername(username));
        return userMapper.toDto(user);
    }

    /**
     * Get all users with pagination.
     */
    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toDto);
    }

    /**
     * Search users by username or email.
     */
    public Page<UserDTO> searchUsers(String query, Pageable pageable) {
        return userRepository.searchUsers(query, pageable).map(userMapper::toDto);
    }

    /**
     * Update user profile.
     *
     * @param userId User ID
     * @param dto    Update data
     * @return Updated user
     */
    @Transactional
    public UserDTO updateUser(UUID userId, UserUpdateDTO dto) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        // Update email if provided and different
        if (dto.email() != null && !dto.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(dto.email())) {
                throw UserExistsException.emailExists(dto.email());
            }
            user.setEmail(dto.email());
            user.setEmailVerified(false); // Require re-verification

            // Send new verification email
            if (emailService.isEmailEnabled()) {
                String token = verificationTokenService.generateEmailVerificationToken(user.getUsername());
                emailService.sendEmailVerification(dto.email(), user.getUsername(), token);
            }
        }

        // Update full name if provided
        if (dto.fullName() != null) {
            user.setFullName(dto.fullName());
        }

        // Update password if provided
        if (dto.newPassword() != null && dto.currentPassword() != null) {
            if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
                throw new AuthException("Current password is incorrect", "INVALID_PASSWORD");
            }
            user.setPassword(passwordEncoder.encode(dto.newPassword()));
            emailService.sendPasswordChangeConfirmation(user.getEmail(), user.getUsername());
        }

        UserEntity savedUser = userRepository.save(user);
        log.info("User profile updated: {}", user.getUsername());

        return userMapper.toDto(savedUser);
    }

    /**
     * Assign roles to a user (admin action).
     *
     * @param userId       Target user ID
     * @param roles        Roles to assign
     * @param adminId      Admin performing the action
     * @param adminUsername Admin username
     * @param ipAddress    Request IP address
     * @param userAgent    Request user agent
     */
    @Transactional
    public UserDTO assignRoles(UUID userId, Set<String> roles, UUID adminId, String adminUsername,
                               String ipAddress, String userAgent) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        // Validate roles
        for (String role : roles) {
            if (!VALID_ROLES.contains(role)) {
                throw new AuthException("Invalid role: " + role, "INVALID_ROLE");
            }
        }

        Set<String> oldRoles = new HashSet<>(user.getRoles());
        user.setRoles(new HashSet<>(roles));

        UserEntity savedUser = userRepository.save(user);
        log.info("Roles updated for user {}: {} -> {}", user.getUsername(), oldRoles, roles);

        // Log audit event
        auditService.publishAuditEvent(
                adminId,
                adminUsername,
                "ASSIGN_ROLES",
                "USER",
                userId.toString(),
                Map.of("roles", oldRoles),
                Map.of("roles", roles),
                ipAddress,
                userAgent
        );

        return userMapper.toDto(savedUser);
    }

    /**
     * Enable or disable a user (admin action).
     */
    @Transactional
    public UserDTO setUserEnabled(UUID userId, boolean enabled, UUID adminId, String adminUsername,
                                  String ipAddress, String userAgent) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        boolean oldEnabled = user.isEnabled();
        user.setEnabled(enabled);

        UserEntity savedUser = userRepository.save(user);
        log.info("User {} {}: {}", enabled ? "enabled" : "disabled", user.getUsername(), userId);

        auditService.publishAuditEvent(
                adminId,
                adminUsername,
                enabled ? "ENABLE_USER" : "DISABLE_USER",
                "USER",
                userId.toString(),
                Map.of("enabled", oldEnabled),
                Map.of("enabled", enabled),
                ipAddress,
                userAgent
        );

        return userMapper.toDto(savedUser);
    }

    /**
     * Lock or unlock a user (admin action).
     */
    @Transactional
    public UserDTO setUserLocked(UUID userId, boolean locked, UUID adminId, String adminUsername,
                                 String ipAddress, String userAgent) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        boolean oldLocked = user.isLocked();
        user.setLocked(locked);

        UserEntity savedUser = userRepository.save(user);
        log.info("User {} {}: {}", locked ? "locked" : "unlocked", user.getUsername(), userId);

        auditService.publishAuditEvent(
                adminId,
                adminUsername,
                locked ? "LOCK_USER" : "UNLOCK_USER",
                "USER",
                userId.toString(),
                Map.of("locked", oldLocked),
                Map.of("locked", locked),
                ipAddress,
                userAgent
        );

        return userMapper.toDto(savedUser);
    }

    /**
     * Delete a user (soft delete not implemented yet, hard delete for now).
     */
    @Transactional
    public void deleteUser(UUID userId, UUID adminId, String adminUsername,
                           String ipAddress, String userAgent) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> UserNotFoundException.byId(userId));

        userRepository.delete(user);
        log.info("User deleted: {}", userId);

        auditService.publishAuditEvent(
                adminId,
                adminUsername,
                "DELETE_USER",
                "USER",
                userId.toString(),
                Map.of(
                        "username", user.getUsername(),
                        "email", user.getEmail(),
                        "roles", user.getRoles()
                ),
                null,
                ipAddress,
                userAgent
        );
    }

    /**
     * Get user entity by username (for internal use).
     */
    public Optional<UserEntity> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}
