package com.shop.ecommerceengine.catalog.repository;

import com.shop.ecommerceengine.catalog.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CategoryEntity with hierarchical queries.
 * Supports tree structure operations for nested categories.
 */
@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    /**
     * Find a category by its unique slug.
     */
    Optional<CategoryEntity> findBySlug(String slug);

    /**
     * Find all active categories ordered by display order.
     */
    List<CategoryEntity> findByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Find all root categories (no parent) that are active.
     */
    List<CategoryEntity> findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAsc();

    /**
     * Find all child categories of a parent.
     */
    List<CategoryEntity> findByParentIdAndActiveTrueOrderByDisplayOrderAsc(UUID parentId);

    /**
     * Find root categories with children eagerly fetched for tree building.
     */
    @Query("SELECT DISTINCT c FROM CategoryEntity c " +
            "LEFT JOIN FETCH c.children " +
            "WHERE c.parentId IS NULL AND c.active = true " +
            "ORDER BY c.displayOrder ASC")
    List<CategoryEntity> findRootCategoriesWithChildren();

    /**
     * Find a category by slug with children eagerly fetched.
     */
    @Query("SELECT c FROM CategoryEntity c " +
            "LEFT JOIN FETCH c.children " +
            "WHERE c.slug = :slug AND c.active = true")
    Optional<CategoryEntity> findBySlugWithChildren(@Param("slug") String slug);

    /**
     * Check if a category with the given slug exists.
     */
    boolean existsBySlug(String slug);

    /**
     * Count active categories.
     */
    long countByActiveTrue();

    /**
     * Find all descendants of a category (recursive via common table expression).
     * Note: This query works on PostgreSQL. For H2 testing, a simpler approach may be needed.
     */
    @Query(value = "WITH RECURSIVE category_tree AS (" +
            "  SELECT id, name, slug, parent_id FROM categories WHERE id = :parentId " +
            "  UNION ALL " +
            "  SELECT c.id, c.name, c.slug, c.parent_id FROM categories c " +
            "  INNER JOIN category_tree ct ON c.parent_id = ct.id" +
            ") SELECT id FROM category_tree WHERE id != :parentId",
            nativeQuery = true)
    List<UUID> findDescendantIds(@Param("parentId") UUID parentId);
}
