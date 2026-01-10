package com.shop.ecommerceengine.catalog.entity;

import com.shop.ecommerceengine.common.converter.JsonMapConverter;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Product entity for the catalog.
 * Uses JSONB for flexible attributes storage.
 * Supports @Version for optimistic locking.
 */
@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "compare_at_price", precision = 19, scale = 4)
    private BigDecimal compareAtPrice;

    @Column(name = "category_id")
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private CategoryEntity category;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "attributes", columnDefinition = "text")
    private Map<String, Object> attributes = new HashMap<>();

    @Column(name = "sku", unique = true, length = 100)
    private String sku;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "featured", nullable = false)
    private boolean featured = false;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "discount_start")
    private LocalDateTime discountStart;

    @Column(name = "discount_end")
    private LocalDateTime discountEnd;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ProductEntity() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (attributes == null) {
            attributes = new HashMap<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getCompareAtPrice() {
        return compareAtPrice;
    }

    public void setCompareAtPrice(BigDecimal compareAtPrice) {
        this.compareAtPrice = compareAtPrice;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public CategoryEntity getCategory() {
        return category;
    }

    public void setCategory(CategoryEntity category) {
        this.category = category;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    public LocalDateTime getDiscountStart() {
        return discountStart;
    }

    public void setDiscountStart(LocalDateTime discountStart) {
        this.discountStart = discountStart;
    }

    public LocalDateTime getDiscountEnd() {
        return discountEnd;
    }

    public void setDiscountEnd(LocalDateTime discountEnd) {
        this.discountEnd = discountEnd;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * Checks if the discount is currently active.
     */
    public boolean isDiscountActive() {
        if (discountPercentage == null || discountPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = discountStart == null || !now.isBefore(discountStart);
        boolean beforeEnd = discountEnd == null || !now.isAfter(discountEnd);
        return afterStart && beforeEnd;
    }

    /**
     * Calculates the effective price after discount.
     */
    public BigDecimal getEffectivePrice() {
        if (!isDiscountActive()) {
            return price;
        }
        BigDecimal discountMultiplier = BigDecimal.ONE.subtract(
                discountPercentage.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP)
        );
        return price.multiply(discountMultiplier).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Checks if the product is soft deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductEntity that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ProductEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", slug='" + slug + '\'' +
                ", price=" + price +
                ", categoryId=" + categoryId +
                ", active=" + active +
                ", featured=" + featured +
                ", discountPercentage=" + discountPercentage +
                ", deletedAt=" + deletedAt +
                '}';
    }
}
