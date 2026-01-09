package com.shop.ecommerceengine.catalog.controller;

import com.shop.ecommerceengine.catalog.dto.BulkDiscountDTO;
import com.shop.ecommerceengine.catalog.dto.BulkDiscountResultDTO;
import com.shop.ecommerceengine.catalog.dto.DiscountDTO;
import com.shop.ecommerceengine.catalog.dto.ProductAdminDTO;
import com.shop.ecommerceengine.catalog.dto.ProductCreateDTO;
import com.shop.ecommerceengine.catalog.service.ImageService;
import com.shop.ecommerceengine.catalog.service.ProductAuditHelper;
import com.shop.ecommerceengine.catalog.service.ProductService;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin controller for product management.
 * Requires ROLE_CONTENT_MANAGER or higher for access.
 */
@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Admin Products", description = "Admin product management endpoints")
@PreAuthorize("hasAnyRole('CONTENT_MANAGER', 'SUPER_ADMIN')")
public class AdminProductController {

    private static final Logger log = LoggerFactory.getLogger(AdminProductController.class);

    private final ProductService productService;
    private final ImageService imageService;
    private final ProductAuditHelper auditHelper;
    private final UserService userService;

    public AdminProductController(ProductService productService,
                                  ImageService imageService,
                                  ProductAuditHelper auditHelper,
                                  UserService userService) {
        this.productService = productService;
        this.imageService = imageService;
        this.auditHelper = auditHelper;
        this.userService = userService;
    }

    /**
     * Get all products for admin (including inactive).
     */
    @GetMapping
    @Operation(summary = "Get all products (admin)", description = "Returns paginated list of all products including inactive")
    public ResponseEntity<ApiResponse<List<ProductAdminDTO>>> getAllProducts(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        log.debug("Admin fetching all products, page: {}, size: {}", page, size);

        Sort sort = Sort.by(
                sortDirection.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy
        );
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductAdminDTO> products = productService.getAllProductsForAdmin(pageable);

        return ResponseEntity.ok(ApiResponse.success(products.getContent()));
    }

    /**
     * Get a single product by ID for admin.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID (admin)", description = "Returns a single product with all admin fields")
    public ResponseEntity<ApiResponse<ProductAdminDTO>> getProductById(
            @Parameter(description = "Product UUID")
            @PathVariable UUID id) {

        log.debug("Admin fetching product by ID: {}", id);
        ProductAdminDTO product = productService.getProductByIdForAdmin(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    /**
     * Create a new product.
     */
    @PostMapping
    @Operation(summary = "Create product", description = "Creates a new product")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createProduct(
            @Valid @RequestBody ProductCreateDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin creating product: {}", request.name());

        Map<String, Object> result = productService.createProduct(request);
        ProductAdminDTO product = (ProductAdminDTO) result.get("product");

        // Audit the creation
        UUID adminId = getAdminId(authentication);
        auditHelper.auditProductCreated(
                adminId,
                authentication.getName(),
                product,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(result, "Product created successfully"));
    }

    /**
     * Update an existing product.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Updates an existing product")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProduct(
            @Parameter(description = "Product UUID")
            @PathVariable UUID id,
            @Valid @RequestBody ProductCreateDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin updating product: {}", id);

        // Get old state for audit
        ProductAdminDTO oldProduct = productService.getProductByIdForAdmin(id);

        Map<String, Object> result = productService.updateProduct(id, request);
        ProductAdminDTO newProduct = (ProductAdminDTO) result.get("product");

        // Audit the update
        UUID adminId = getAdminId(authentication);
        auditHelper.auditProductUpdated(
                adminId,
                authentication.getName(),
                oldProduct,
                newProduct,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        // Include warning in response if present
        @SuppressWarnings("unchecked")
        Map<String, Object> warnings = (Map<String, Object>) result.get("warnings");
        String message = "Product updated successfully";
        if (warnings != null && warnings.containsKey("priceChangeWarning")) {
            message = "Product updated. WARNING: " + warnings.get("priceChangeWarning");
        }

        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    /**
     * Soft delete a product.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product (soft)", description = "Soft deletes a product")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Parameter(description = "Product UUID")
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin deleting product: {}", id);

        // Get product for audit before deletion
        ProductAdminDTO product = productService.getProductByIdForAdmin(id);

        productService.softDeleteProduct(id);

        // Audit the deletion
        UUID adminId = getAdminId(authentication);
        auditHelper.auditProductDeleted(
                adminId,
                authentication.getName(),
                product,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(null, "Product deleted successfully"));
    }

    /**
     * Set discount on a product.
     */
    @PostMapping("/{id}/discount")
    @Operation(summary = "Set product discount", description = "Sets a discount on a product")
    public ResponseEntity<ApiResponse<ProductAdminDTO>> setDiscount(
            @Parameter(description = "Product UUID")
            @PathVariable UUID id,
            @Valid @RequestBody DiscountDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin setting discount on product {}: {}%", id, request.discountPercentage());

        // Get old discount for audit
        ProductAdminDTO oldProduct = productService.getProductByIdForAdmin(id);

        ProductAdminDTO product = productService.setDiscount(id, request);

        // Audit the discount change
        UUID adminId = getAdminId(authentication);
        auditHelper.auditDiscountSet(
                adminId,
                authentication.getName(),
                id,
                oldProduct.discountPercentage(),
                request.discountPercentage(),
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(product, "Discount applied successfully"));
    }

    /**
     * Remove discount from a product.
     */
    @DeleteMapping("/{id}/discount")
    @Operation(summary = "Remove product discount", description = "Removes discount from a product")
    public ResponseEntity<ApiResponse<ProductAdminDTO>> removeDiscount(
            @Parameter(description = "Product UUID")
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin removing discount from product: {}", id);

        // Get old discount for audit
        ProductAdminDTO oldProduct = productService.getProductByIdForAdmin(id);

        ProductAdminDTO product = productService.removeDiscount(id);

        // Audit the discount removal
        UUID adminId = getAdminId(authentication);
        auditHelper.auditDiscountSet(
                adminId,
                authentication.getName(),
                id,
                oldProduct.discountPercentage(),
                null,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(product, "Discount removed successfully"));
    }

    /**
     * Apply bulk discount to products.
     */
    @PostMapping("/bulk-discount")
    @Operation(summary = "Apply bulk discount", description = "Applies discount to multiple products by category or all")
    public ResponseEntity<ApiResponse<BulkDiscountResultDTO>> bulkSetDiscount(
            @Valid @RequestBody BulkDiscountDTO request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin applying bulk discount: {}%", request.discountPercentage());

        BulkDiscountResultDTO result = productService.bulkSetDiscount(request);

        // Audit the bulk operation
        UUID adminId = getAdminId(authentication);
        auditHelper.auditBulkDiscount(
                adminId,
                authentication.getName(),
                result.scope(),
                result.updatedCount(),
                result.discountPercentage(),
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(result,
                String.format("Bulk discount applied to %d products", result.updatedCount())));
    }

    /**
     * Upload product image.
     */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload product image", description = "Uploads and processes product image in multiple sizes")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(
            @Parameter(description = "Product UUID")
            @PathVariable UUID id,
            @Parameter(description = "Image file")
            @RequestParam("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Admin uploading image for product: {}", id);

        // Verify product exists
        productService.getProductByIdForAdmin(id);

        Map<String, String> imageUrls = imageService.uploadProductImage(id, file);

        // Audit the image upload
        UUID adminId = getAdminId(authentication);
        auditHelper.auditImageUploaded(
                adminId,
                authentication.getName(),
                id,
                imageUrls,
                getClientIp(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity.ok(ApiResponse.success(imageUrls, "Image uploaded successfully"));
    }

    /**
     * Get admin user ID from authentication.
     */
    private UUID getAdminId(Authentication authentication) {
        try {
            return userService.getUserByUsername(authentication.getName()).id();
        } catch (Exception e) {
            log.warn("Could not get admin user ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
