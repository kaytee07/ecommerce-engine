package com.shop.ecommerceengine.identity.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary in-memory user details service for development.
 * Will be replaced with database-backed implementation in Phase 3.
 */
@Service
public class InMemoryUserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserService.class);

    private final PasswordEncoder passwordEncoder;
    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();

    public InMemoryUserService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        // Create test users for development
        createUser("testuser", "password123", "ROLE_USER");
        createUser("admin", "admin123", "ROLE_ADMIN", "ROLE_USER");
        createUser("superadmin", "super123", "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_USER");
        createUser("contentmanager", "content123", "ROLE_CONTENT_MANAGER", "ROLE_USER");
        createUser("supportagent", "support123", "ROLE_SUPPORT_AGENT", "ROLE_USER");
        createUser("warehouse", "warehouse123", "ROLE_WAREHOUSE", "ROLE_USER");

        log.info("Initialized {} in-memory test users", users.size());
    }

    private void createUser(String username, String password, String... roles) {
        String encodedPassword = passwordEncoder.encode(password);
        // Roles stored with ROLE_ prefix for consistency
        UserRecord record = new UserRecord(username, encodedPassword, roles);
        users.put(username, record);
        log.debug("Created test user: {} with roles: {}", username, String.join(", ", roles));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserRecord record = users.get(username);
        if (record == null) {
            log.warn("User not found: {}", username);
            throw new UsernameNotFoundException("User not found: " + username);
        }

        // Return a fresh copy each time to avoid credential erasure issues
        // Spring Security's User.builder().roles() expects roles WITHOUT the ROLE_ prefix
        String[] rolesWithoutPrefix = java.util.Arrays.stream(record.roles())
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .toArray(String[]::new);

        return User.builder()
                .username(record.username())
                .password(record.encodedPassword())
                .roles(rolesWithoutPrefix)
                .build();
    }

    /**
     * Immutable record to store user data.
     * Password is already encoded.
     */
    private record UserRecord(String username, String encodedPassword, String[] roles) {}
}
