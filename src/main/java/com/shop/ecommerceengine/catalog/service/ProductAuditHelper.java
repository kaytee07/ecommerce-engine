package com.shop.ecommerceengine.catalog.service;

import com.shop.ecommerceengine.catalog.dto.ProductAdminDTO;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import com.shop.ecommerceengine.identity.service.AuditService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper for publishing product-related audit events.
 * Simplifies auditing of product CRUD and discount operations.
 */
@Component
public class ProductAuditHelper {

    private static final String ENTITY_TYPE = "PRODUCT";

    private final AuditService auditService;

    public ProductAuditHelper(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Audit product creation.
     */
    public void auditProductCreated(UUID adminId, String adminUsername,
                                     ProductAdminDTO product,
                                     String ipAddress, String userAgent) {
        // Skip auditing if adminId is null (e.g., in tests)
        if (adminId == null) {
            return;
        }
        Map<String, Object> newValue = productToMap(product);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "PRODUCT_CREATED",
                ENTITY_TYPE,
                product.id().toString(),
                null,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Audit product update.
     */
    public void auditProductUpdated(UUID adminId, String adminUsername,
                                     ProductAdminDTO oldProduct, ProductAdminDTO newProduct,
                                     String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }
        Map<String, Object> oldValue = productToMap(oldProduct);
        Map<String, Object> newValue = productToMap(newProduct);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "PRODUCT_UPDATED",
                ENTITY_TYPE,
                newProduct.id().toString(),
                oldValue,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Audit product soft delete.
     */
    public void auditProductDeleted(UUID adminId, String adminUsername,
                                     ProductAdminDTO product,
                                     String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }
        Map<String, Object> oldValue = productToMap(product);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "PRODUCT_DELETED",
                ENTITY_TYPE,
                product.id().toString(),
                oldValue,
                null,
                ipAddress, userAgent
        );
    }

    /**
     * Audit discount set on product.
     */
    public void auditDiscountSet(UUID adminId, String adminUsername,
                                  UUID productId, BigDecimal oldDiscount, BigDecimal newDiscount,
                                  String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }
        Map<String, Object> oldValue = new HashMap<>();
        oldValue.put("discountPercentage", oldDiscount);

        Map<String, Object> newValue = new HashMap<>();
        newValue.put("discountPercentage", newDiscount);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "PRODUCT_DISCOUNT_SET",
                ENTITY_TYPE,
                productId.toString(),
                oldValue,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Audit bulk discount operation.
     */
    public void auditBulkDiscount(UUID adminId, String adminUsername,
                                   String scope, int count, BigDecimal discountPercentage,
                                   String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }
        Map<String, Object> newValue = new HashMap<>();
        newValue.put("scope", scope);
        newValue.put("updatedCount", count);
        newValue.put("discountPercentage", discountPercentage);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "PRODUCT_BULK_DISCOUNT",
                ENTITY_TYPE,
                scope,
                null,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Audit image upload.
     */
    public void auditImageUploaded(UUID adminId, String adminUsername,
                                    UUID productId, Map<String, String> imageUrls,
                                    String ipAddress, String userAgent) {
        if (adminId == null) {
            return;
        }
        Map<String, Object> newValue = new HashMap<>();
        newValue.put("imageUrls", imageUrls);

        auditService.publishAuditEvent(
                adminId, adminUsername,
                "PRODUCT_IMAGE_UPLOADED",
                ENTITY_TYPE,
                productId.toString(),
                null,
                newValue,
                ipAddress, userAgent
        );
    }

    /**
     * Convert ProductAdminDTO to a map for audit logging.
     */
    private Map<String, Object> productToMap(ProductAdminDTO product) {
        if (product == null) {
            return null;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("id", product.id());
        map.put("name", product.name());
        map.put("slug", product.slug());
        map.put("price", product.price());
        map.put("categoryId", product.categoryId());
        map.put("active", product.active());
        map.put("featured", product.featured());
        map.put("discountPercentage", product.discountPercentage());
        map.put("sku", product.sku());
        return map;
    }
}
