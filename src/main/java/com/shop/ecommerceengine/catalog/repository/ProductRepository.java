package com.shop.ecommerceengine.catalog.repository;

import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ProductEntity with custom search queries.
 * Supports full-text search, category filtering, and pagination.
 */
@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    /**
     * Find a product by its unique slug.
     */
    Optional<ProductEntity> findBySlug(String slug);

    /**
     * Find a product by its unique SKU.
     */
    Optional<ProductEntity> findBySku(String sku);

    /**
     * Find all active products.
     */
    Page<ProductEntity> findByActiveTrue(Pageable pageable);

    /**
     * Find all featured and active products.
     */
    List<ProductEntity> findByFeaturedTrueAndActiveTrueOrderByCreatedAtDesc();

    /**
     * Find products by category ID.
     */
    Page<ProductEntity> findByCategoryIdAndActiveTrue(UUID categoryId, Pageable pageable);

    /**
     * Find products by category slug.
     */
    @Query("SELECT p FROM ProductEntity p JOIN p.category c WHERE c.slug = :categorySlug AND p.active = true")
    Page<ProductEntity> findByCategorySlugAndActiveTrue(@Param("categorySlug") String categorySlug, Pageable pageable);

    /**
     * Search products by name or description (case-insensitive contains).
     * This is a fallback for databases without full-text search support.
     */
    @Query("SELECT p FROM ProductEntity p WHERE p.active = true AND " +
            "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<ProductEntity> searchByNameOrDescription(@Param("query") String query, Pageable pageable);

    /**
     * Search products with optional category filter.
     */
    @Query("SELECT p FROM ProductEntity p WHERE p.active = true AND " +
            "(:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "(:categoryId IS NULL OR p.categoryId = :categoryId)")
    Page<ProductEntity> searchWithFilters(
            @Param("query") String query,
            @Param("categoryId") UUID categoryId,
            Pageable pageable);

    /**
     * Search products with price range filter.
     */
    @Query("SELECT p FROM ProductEntity p WHERE p.active = true AND " +
            "(:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "(:categoryId IS NULL OR p.categoryId = :categoryId) AND " +
            "(:minPrice IS NULL OR p.price >= :minPrice) AND " +
            "(:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<ProductEntity> searchWithPriceRange(
            @Param("query") String query,
            @Param("categoryId") UUID categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    /**
     * Count products by category.
     */
    long countByCategoryIdAndActiveTrue(UUID categoryId);

    /**
     * Check if a product with the given slug exists.
     */
    boolean existsBySlug(String slug);

    /**
     * Check if a product with the given SKU exists.
     */
    boolean existsBySku(String sku);

    // ============= Admin Methods =============

    /**
     * Find all products including inactive ones (for admin).
     */
    Page<ProductEntity> findByDeletedAtIsNull(Pageable pageable);

    /**
     * Find product by ID only if not deleted.
     */
    @Query("SELECT p FROM ProductEntity p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<ProductEntity> findByIdAndNotDeleted(@Param("id") UUID id);

    /**
     * Find all products in a category (for bulk operations).
     */
    @Query("SELECT p FROM ProductEntity p WHERE p.categoryId = :categoryId AND p.deletedAt IS NULL")
    List<ProductEntity> findByCategoryIdAndNotDeleted(@Param("categoryId") UUID categoryId);

    /**
     * Find all non-deleted products (for bulk operations).
     */
    @Query("SELECT p FROM ProductEntity p WHERE p.deletedAt IS NULL")
    List<ProductEntity> findAllNotDeleted();

    /**
     * Bulk update discount for products in a category.
     */
    @Modifying
    @Query("UPDATE ProductEntity p SET p.discountPercentage = :percentage, " +
            "p.discountStart = :startDate, p.discountEnd = :endDate " +
            "WHERE p.categoryId = :categoryId AND p.deletedAt IS NULL")
    int bulkUpdateDiscountByCategory(
            @Param("categoryId") UUID categoryId,
            @Param("percentage") BigDecimal percentage,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Bulk update discount for all products.
     */
    @Modifying
    @Query("UPDATE ProductEntity p SET p.discountPercentage = :percentage, " +
            "p.discountStart = :startDate, p.discountEnd = :endDate " +
            "WHERE p.deletedAt IS NULL")
    int bulkUpdateDiscountForAll(
            @Param("percentage") BigDecimal percentage,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Soft delete a product.
     */
    @Modifying
    @Query("UPDATE ProductEntity p SET p.deletedAt = :deletedAt WHERE p.id = :id")
    int softDelete(@Param("id") UUID id, @Param("deletedAt") Instant deletedAt);

    /**
     * Count products by category (including inactive, excluding deleted).
     */
    long countByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    /**
     * Count all non-deleted products.
     */
    long countByDeletedAtIsNull();
}
