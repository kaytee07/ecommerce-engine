package com.shop.ecommerceengine.catalog.service;

import com.shop.ecommerceengine.catalog.dto.BulkDiscountDTO;
import com.shop.ecommerceengine.catalog.dto.BulkDiscountResultDTO;
import com.shop.ecommerceengine.catalog.dto.DiscountDTO;
import com.shop.ecommerceengine.catalog.dto.ProductAdminDTO;
import com.shop.ecommerceengine.catalog.dto.ProductCreateDTO;
import com.shop.ecommerceengine.catalog.dto.ProductDTO;
import com.shop.ecommerceengine.catalog.dto.ProductSearchRequest;
import com.shop.ecommerceengine.catalog.entity.CategoryEntity;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.catalog.exception.InvalidDiscountException;
import com.shop.ecommerceengine.catalog.exception.ProductNotFoundException;
import com.shop.ecommerceengine.catalog.mapper.ProductMapper;
import com.shop.ecommerceengine.catalog.repository.CategoryRepository;
import com.shop.ecommerceengine.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for product operations.
 * Uses Redis caching for frequently accessed data with 10-minute TTL.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final BigDecimal PRICE_CHANGE_WARNING_THRESHOLD = new BigDecimal("50");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ProductService(ProductRepository productRepository,
                         CategoryRepository categoryRepository,
                         ProductMapper productMapper,
                         ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMapper = productMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Get all active products with pagination.
     */
    @Cacheable(value = "products", key = "'all:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        log.debug("Fetching all active products, page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findByActiveTrue(pageable)
                .map(productMapper::toDTO);
    }

    /**
     * Get a product by ID.
     */
    @Cacheable(value = "products", key = "'id:' + #id")
    public ProductDTO getProductById(UUID id) {
        log.debug("Fetching product by ID: {}", id);
        ProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return productMapper.toDTO(product);
    }

    /**
     * Get a product by slug.
     */
    @Cacheable(value = "products", key = "'slug:' + #slug")
    public ProductDTO getProductBySlug(String slug) {
        log.debug("Fetching product by slug: {}", slug);
        ProductEntity product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ProductNotFoundException(slug));
        return productMapper.toDTO(product);
    }

    /**
     * Get all featured products.
     */
    @Cacheable(value = "products", key = "'featured'")
    public List<ProductDTO> getFeaturedProducts() {
        log.debug("Fetching featured products");
        List<ProductEntity> products = productRepository.findByFeaturedTrueAndActiveTrueOrderByCreatedAtDesc();
        return productMapper.toDTOList(products);
    }

    /**
     * Search products with filters.
     */
    public Page<ProductDTO> searchProducts(ProductSearchRequest request) {
        log.debug("Searching products with query: {}, categoryId: {}",
                request.q(), request.categoryId());

        Sort sort = Sort.by(
                request.effectiveSortDirection().equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                request.effectiveSortBy()
        );

        Pageable pageable = PageRequest.of(
                request.effectivePage(),
                request.effectiveSize(),
                sort
        );

        // Use category slug lookup if provided instead of ID
        UUID categoryId = request.categoryId();

        return productRepository.searchWithPriceRange(
                request.q(),
                categoryId,
                request.minPrice(),
                request.maxPrice(),
                pageable
        ).map(productMapper::toDTO);
    }

    /**
     * Get products by category ID.
     */
    @Cacheable(value = "products", key = "'category:' + #categoryId + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ProductDTO> getProductsByCategoryId(UUID categoryId, Pageable pageable) {
        log.debug("Fetching products for category: {}", categoryId);
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(productMapper::toDTO);
    }

    /**
     * Get products by category slug.
     */
    @Cacheable(value = "products", key = "'categorySlug:' + #categorySlug + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<ProductDTO> getProductsByCategorySlug(String categorySlug, Pageable pageable) {
        log.debug("Fetching products for category slug: {}", categorySlug);
        return productRepository.findByCategorySlugAndActiveTrue(categorySlug, pageable)
                .map(productMapper::toDTO);
    }

    /**
     * Count products by category.
     */
    public long countProductsByCategory(UUID categoryId) {
        return productRepository.countByCategoryIdAndActiveTrue(categoryId);
    }

    // ============= Admin Methods =============

    /**
     * Get all products for admin (including inactive, excluding deleted).
     */
    public Page<ProductAdminDTO> getAllProductsForAdmin(Pageable pageable) {
        log.debug("Admin fetching all products, page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findByDeletedAtIsNull(pageable)
                .map(productMapper::toAdminDTO);
    }

    /**
     * Get a product by ID for admin (including inactive, excluding deleted).
     */
    public ProductAdminDTO getProductByIdForAdmin(UUID id) {
        log.debug("Admin fetching product by ID: {}", id);
        ProductEntity product = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return productMapper.toAdminDTO(product);
    }

    /**
     * Create a new product.
     * Returns a map with the product and any warnings.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public Map<String, Object> createProduct(ProductCreateDTO dto) {
        log.info("Creating new product: {}", dto.name());

        ProductEntity entity = productMapper.toEntity(dto);

        // Set category if provided
        if (dto.categoryId() != null) {
            CategoryEntity category = categoryRepository.findById(dto.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + dto.categoryId()));
            entity.setCategory(category);
            entity.setCategoryId(dto.categoryId());
        }

        ProductEntity saved = productRepository.save(entity);
        log.info("Created product with ID: {}", saved.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("product", productMapper.toAdminDTO(saved));
        return result;
    }

    /**
     * Update an existing product.
     * Returns a map with the product and any warnings (e.g., price change > 50%).
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public Map<String, Object> updateProduct(UUID id, ProductCreateDTO dto) {
        log.info("Updating product: {}", id);

        ProductEntity entity = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        BigDecimal oldPrice = entity.getPrice();
        Map<String, Object> warnings = new HashMap<>();

        // Update fields
        productMapper.updateEntity(dto, entity);

        // Set category if provided
        if (dto.categoryId() != null) {
            CategoryEntity category = categoryRepository.findById(dto.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + dto.categoryId()));
            entity.setCategory(category);
            entity.setCategoryId(dto.categoryId());
        }

        // Check for significant price change
        if (dto.price() != null && oldPrice != null) {
            BigDecimal priceChange = calculatePriceChangePercentage(oldPrice, dto.price());
            if (priceChange.abs().compareTo(PRICE_CHANGE_WARNING_THRESHOLD) > 0) {
                warnings.put("priceChangeWarning", String.format(
                        "Price changed by %.1f%% (from %s to %s)",
                        priceChange.doubleValue(), oldPrice, dto.price()));
                log.warn("Significant price change for product {}: {}% (from {} to {})",
                        id, priceChange, oldPrice, dto.price());
            }
        }

        ProductEntity saved = productRepository.save(entity);
        log.info("Updated product: {}", id);

        Map<String, Object> result = new HashMap<>();
        result.put("product", productMapper.toAdminDTO(saved));
        if (!warnings.isEmpty()) {
            result.put("warnings", warnings);
        }
        return result;
    }

    /**
     * Soft delete a product.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public void softDeleteProduct(UUID id) {
        log.info("Soft deleting product: {}", id);

        ProductEntity entity = productRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        entity.setDeletedAt(Instant.now());
        productRepository.save(entity);
        log.info("Soft deleted product: {}", id);
    }

    /**
     * Set discount on a single product.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductAdminDTO setDiscount(UUID productId, DiscountDTO discountDTO) {
        log.info("Setting discount on product {}: {}%", productId, discountDTO.discountPercentage());

        // Validate discount
        validateDiscount(discountDTO);

        ProductEntity entity = productRepository.findByIdAndNotDeleted(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        entity.setDiscountPercentage(discountDTO.discountPercentage());
        entity.setDiscountStart(discountDTO.startDate());
        entity.setDiscountEnd(discountDTO.endDate());

        ProductEntity saved = productRepository.save(entity);
        log.info("Set discount on product {}: {}%", productId, discountDTO.discountPercentage());

        return productMapper.toAdminDTO(saved);
    }

    /**
     * Remove discount from a single product.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductAdminDTO removeDiscount(UUID productId) {
        log.info("Removing discount from product: {}", productId);

        ProductEntity entity = productRepository.findByIdAndNotDeleted(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        entity.setDiscountPercentage(null);
        entity.setDiscountStart(null);
        entity.setDiscountEnd(null);

        ProductEntity saved = productRepository.save(entity);
        log.info("Removed discount from product: {}", productId);

        return productMapper.toAdminDTO(saved);
    }

    /**
     * Apply bulk discount to products by category or all products.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public BulkDiscountResultDTO bulkSetDiscount(BulkDiscountDTO bulkDTO) {
        log.info("Applying bulk discount: {}%", bulkDTO.discountPercentage());

        // Validate target
        if (!bulkDTO.hasValidTarget()) {
            throw InvalidDiscountException.noTargetSpecified();
        }

        // Validate date range
        if (!bulkDTO.hasValidDateRange()) {
            throw InvalidDiscountException.invalidDateRange();
        }

        int updatedCount;
        String scope;

        if (bulkDTO.shouldApplyToAll()) {
            log.info("Applying discount to ALL products");
            updatedCount = productRepository.bulkUpdateDiscountForAll(
                    bulkDTO.discountPercentage(),
                    bulkDTO.startDate(),
                    bulkDTO.endDate()
            );
            scope = "ALL";
        } else {
            log.info("Applying discount to category: {}", bulkDTO.categoryId());
            // Verify category exists
            if (!categoryRepository.existsById(bulkDTO.categoryId())) {
                throw new IllegalArgumentException("Category not found: " + bulkDTO.categoryId());
            }
            updatedCount = productRepository.bulkUpdateDiscountByCategory(
                    bulkDTO.categoryId(),
                    bulkDTO.discountPercentage(),
                    bulkDTO.startDate(),
                    bulkDTO.endDate()
            );
            scope = "CATEGORY:" + bulkDTO.categoryId();
        }

        log.info("Bulk discount applied to {} products", updatedCount);

        return new BulkDiscountResultDTO(
                updatedCount,
                bulkDTO.discountPercentage(),
                scope
        );
    }

    /**
     * Remove discount from all products in a category or all products.
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public BulkDiscountResultDTO bulkRemoveDiscount(UUID categoryId, boolean applyToAll) {
        log.info("Removing bulk discount, categoryId: {}, applyToAll: {}", categoryId, applyToAll);

        int updatedCount;
        String scope;

        if (applyToAll) {
            updatedCount = productRepository.bulkUpdateDiscountForAll(null, null, null);
            scope = "ALL";
        } else if (categoryId != null) {
            updatedCount = productRepository.bulkUpdateDiscountByCategory(categoryId, null, null, null);
            scope = "CATEGORY:" + categoryId;
        } else {
            throw InvalidDiscountException.noTargetSpecified();
        }

        log.info("Bulk discount removed from {} products", updatedCount);

        return new BulkDiscountResultDTO(updatedCount, BigDecimal.ZERO, scope);
    }

    /**
     * Validate discount DTO.
     */
    private void validateDiscount(DiscountDTO discountDTO) {
        BigDecimal percentage = discountDTO.discountPercentage();
        if (percentage.compareTo(BigDecimal.ZERO) < 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw InvalidDiscountException.percentageOutOfRange(percentage);
        }
        if (!discountDTO.hasValidDateRange()) {
            throw InvalidDiscountException.invalidDateRange();
        }
    }

    /**
     * Calculate the percentage change between two prices.
     */
    private BigDecimal calculatePriceChangePercentage(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        return newPrice.subtract(oldPrice)
                .divide(oldPrice, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
