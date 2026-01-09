package com.shop.ecommerceengine.catalog.service;

import com.shop.ecommerceengine.catalog.dto.CategoryDTO;
import com.shop.ecommerceengine.catalog.dto.CategoryTreeDTO;
import com.shop.ecommerceengine.catalog.entity.CategoryEntity;
import com.shop.ecommerceengine.catalog.exception.CategoryNotFoundException;
import com.shop.ecommerceengine.catalog.mapper.CategoryMapper;
import com.shop.ecommerceengine.catalog.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for category operations.
 * Uses Redis caching for frequently accessed data with 10-minute TTL.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    /**
     * Get all active categories.
     */
    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryDTO> getAllCategories() {
        log.debug("Fetching all active categories");
        List<CategoryEntity> categories = categoryRepository.findByActiveTrueOrderByDisplayOrderAsc();
        return categoryMapper.toDTOList(categories);
    }

    /**
     * Get a category by ID.
     */
    @Cacheable(value = "categories", key = "'id:' + #id")
    public CategoryDTO getCategoryById(UUID id) {
        log.debug("Fetching category by ID: {}", id);
        CategoryEntity category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        return categoryMapper.toDTO(category);
    }

    /**
     * Get a category by slug.
     */
    @Cacheable(value = "categories", key = "'slug:' + #slug")
    public CategoryDTO getCategoryBySlug(String slug) {
        log.debug("Fetching category by slug: {}", slug);
        CategoryEntity category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CategoryNotFoundException(slug));
        return categoryMapper.toDTO(category);
    }

    /**
     * Get category tree (hierarchical structure).
     * Returns only root categories with their children nested.
     */
    @Cacheable(value = "categories", key = "'tree'")
    public List<CategoryTreeDTO> getCategoryTree() {
        log.debug("Fetching category tree");
        List<CategoryEntity> rootCategories = categoryRepository.findRootCategoriesWithChildren();
        return categoryMapper.toTreeDTOList(rootCategories);
    }

    /**
     * Get child categories of a parent.
     */
    @Cacheable(value = "categories", key = "'children:' + #parentId")
    public List<CategoryDTO> getChildCategories(UUID parentId) {
        log.debug("Fetching child categories for parent: {}", parentId);
        List<CategoryEntity> children = categoryRepository.findByParentIdAndActiveTrueOrderByDisplayOrderAsc(parentId);
        return categoryMapper.toDTOList(children);
    }

    /**
     * Get root categories (no parent).
     */
    @Cacheable(value = "categories", key = "'roots'")
    public List<CategoryDTO> getRootCategories() {
        log.debug("Fetching root categories");
        List<CategoryEntity> roots = categoryRepository.findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAsc();
        return categoryMapper.toDTOList(roots);
    }

    /**
     * Check if a category exists.
     */
    public boolean categoryExists(UUID id) {
        return categoryRepository.existsById(id);
    }

    /**
     * Count active categories.
     */
    public long countActiveCategories() {
        return categoryRepository.countByActiveTrue();
    }
}
