package com.shop.ecommerceengine.identity.repository;

import com.shop.ecommerceengine.identity.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserEntity with custom queries for user management.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Find user by username (case-insensitive).
     */
    Optional<UserEntity> findByUsernameIgnoreCase(String username);

    /**
     * Find user by exact username.
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * Find user by email (case-insensitive).
     */
    Optional<UserEntity> findByEmailIgnoreCase(String email);

    /**
     * Check if username exists (case-insensitive).
     */
    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Check if email exists (case-insensitive).
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Find all enabled users with pagination.
     */
    Page<UserEntity> findByEnabledTrue(Pageable pageable);

    /**
     * Find users by role using LIKE on JSON string representation.
     * Works with both PostgreSQL JSONB and H2 for testing.
     * Usage: findByRole("ROLE_ADMIN")
     */
    @Query(value = "SELECT * FROM users u WHERE u.roles LIKE CONCAT('%', :role, '%')", nativeQuery = true)
    Page<UserEntity> findByRole(@Param("role") String role, Pageable pageable);

    /**
     * Search users by username or email.
     */
    @Query("SELECT u FROM UserEntity u WHERE " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<UserEntity> searchUsers(@Param("query") String query, Pageable pageable);
}
