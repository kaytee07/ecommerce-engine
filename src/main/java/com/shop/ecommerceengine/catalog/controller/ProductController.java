package com.shop.ecommerceengine.catalog.controller;

import com.shop.ecommerceengine.catalog.dto.ProductDTO;
import com.shop.ecommerceengine.catalog.dto.ProductSearchRequest;
import com.shop.ecommerceengine.catalog.service.ProductService;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Storefront controller for product operations.
 * Public endpoints for browsing and searching products.
 */
@RestController
@RequestMapping("/api/v1/store/products")
@Tag(name = "Products", description = "Storefront product browsing and search")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Get all products", description = "Returns paginated list of active products")
    public ResponseEntity<ApiResponse<List<ProductDTO>>> getAllProducts(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Sort sort = Sort.by(
                sortDirection.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy
        );
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductDTO> products = productService.getAllProducts(pageable);

        return ResponseEntity.ok(ApiResponse.success(products.getContent()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID", description = "Returns a single product by its UUID")
    public ResponseEntity<ApiResponse<ProductDTO>> getProductById(
            @Parameter(description = "Product UUID")
            @PathVariable UUID id) {

        ProductDTO product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get product by slug", description = "Returns a single product by its slug")
    public ResponseEntity<ApiResponse<ProductDTO>> getProductBySlug(
            @Parameter(description = "Product slug")
            @PathVariable String slug) {

        ProductDTO product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured products", description = "Returns list of featured products")
    public ResponseEntity<ApiResponse<List<ProductDTO>>> getFeaturedProducts() {
        List<ProductDTO> products = productService.getFeaturedProducts();
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products", description = "Search products with filters and pagination")
    public ResponseEntity<ApiResponse<List<ProductDTO>>> searchProducts(
            @Parameter(description = "Search query")
            @RequestParam(required = false) String q,
            @Parameter(description = "Category slug filter")
            @RequestParam(required = false) String category,
            @Parameter(description = "Category ID filter")
            @RequestParam(required = false) UUID categoryId,
            @Parameter(description = "Minimum price filter")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price filter")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Featured filter")
            @RequestParam(required = false) Boolean featured,
            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        ProductSearchRequest request = new ProductSearchRequest(
                q, category, categoryId, minPrice, maxPrice, featured, null, sortBy, sortDirection, page, size
        );

        Page<ProductDTO> products = productService.searchProducts(request);
        return ResponseEntity.ok(ApiResponse.success(products.getContent()));
    }
}
