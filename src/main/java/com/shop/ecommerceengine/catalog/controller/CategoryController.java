package com.shop.ecommerceengine.catalog.controller;

import com.shop.ecommerceengine.catalog.dto.CategoryDTO;
import com.shop.ecommerceengine.catalog.dto.CategoryTreeDTO;
import com.shop.ecommerceengine.catalog.dto.ProductDTO;
import com.shop.ecommerceengine.catalog.service.CategoryService;
import com.shop.ecommerceengine.catalog.service.ProductService;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Storefront controller for category operations.
 * Public endpoints for browsing categories and their products.
 */
@RestController
@RequestMapping("/api/v1/store/categories")
@Tag(name = "Categories", description = "Storefront category browsing")
public class CategoryController {

    private final CategoryService categoryService;
    private final ProductService productService;

    public CategoryController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Get all categories", description = "Returns list of all active categories")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getAllCategories() {
        List<CategoryDTO> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Returns a single category by its UUID")
    public ResponseEntity<ApiResponse<CategoryDTO>> getCategoryById(
            @Parameter(description = "Category UUID")
            @PathVariable UUID id) {

        CategoryDTO category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get category by slug", description = "Returns a single category by its slug")
    public ResponseEntity<ApiResponse<CategoryDTO>> getCategoryBySlug(
            @Parameter(description = "Category slug")
            @PathVariable String slug) {

        CategoryDTO category = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @GetMapping("/tree")
    @Operation(summary = "Get category tree", description = "Returns hierarchical category tree with nested children")
    public ResponseEntity<ApiResponse<List<CategoryTreeDTO>>> getCategoryTree() {
        List<CategoryTreeDTO> tree = categoryService.getCategoryTree();
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    @GetMapping("/{id}/products")
    @Operation(summary = "Get products by category", description = "Returns paginated products in a category")
    public ResponseEntity<ApiResponse<List<ProductDTO>>> getProductsByCategory(
            @Parameter(description = "Category UUID")
            @PathVariable UUID id,
            @Parameter(description = "Page number")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field")
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction")
            @RequestParam(defaultValue = "desc") String sortDirection) {

        Sort sort = Sort.by(
                sortDirection.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy
        );
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<ProductDTO> products = productService.getProductsByCategoryId(id, pageable);

        return ResponseEntity.ok(ApiResponse.success(products.getContent()));
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Get child categories", description = "Returns child categories of a parent category")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getChildCategories(
            @Parameter(description = "Parent category UUID")
            @PathVariable UUID id) {

        List<CategoryDTO> children = categoryService.getChildCategories(id);
        return ResponseEntity.ok(ApiResponse.success(children));
    }
}
