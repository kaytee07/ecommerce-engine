package com.shop.ecommerceengine.catalog.mapper;

import com.shop.ecommerceengine.catalog.dto.ProductAdminDTO;
import com.shop.ecommerceengine.catalog.dto.ProductCreateDTO;
import com.shop.ecommerceengine.catalog.dto.ProductDTO;
import com.shop.ecommerceengine.catalog.entity.ProductEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * MapStruct mapper for Product entity and DTOs.
 * Uses Spring component model for dependency injection.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProductMapper {

    /**
     * Maps ProductEntity to ProductDTO for storefront.
     * Includes active discount information.
     */
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "currentDiscountPercentage", expression = "java(entity.isDiscountActive() ? entity.getDiscountPercentage() : null)")
    @Mapping(target = "effectivePrice", expression = "java(entity.getEffectivePrice())")
    ProductDTO toDTO(ProductEntity entity);

    /**
     * Maps a list of ProductEntity to a list of ProductDTO.
     */
    List<ProductDTO> toDTOList(List<ProductEntity> entities);

    /**
     * Maps ProductEntity to ProductAdminDTO with full admin fields.
     */
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "discountActive", expression = "java(entity.isDiscountActive())")
    @Mapping(target = "effectivePrice", expression = "java(entity.getEffectivePrice())")
    ProductAdminDTO toAdminDTO(ProductEntity entity);

    /**
     * Maps a list of ProductEntity to a list of ProductAdminDTO.
     */
    List<ProductAdminDTO> toAdminDTOList(List<ProductEntity> entities);

    /**
     * Maps ProductCreateDTO to ProductEntity for creation.
     * ID, version, timestamps, and discount fields are managed separately.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "discountPercentage", ignore = true)
    @Mapping(target = "discountStart", ignore = true)
    @Mapping(target = "discountEnd", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "slug", expression = "java(dto.effectiveSlug())")
    @Mapping(target = "active", expression = "java(dto.effectiveActive())")
    @Mapping(target = "featured", expression = "java(dto.effectiveFeatured())")
    ProductEntity toEntity(ProductCreateDTO dto);

    /**
     * Updates an existing ProductEntity from a ProductCreateDTO.
     * Null values in DTO are ignored. Discount fields are managed separately.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "discountPercentage", ignore = true)
    @Mapping(target = "discountStart", ignore = true)
    @Mapping(target = "discountEnd", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntity(ProductCreateDTO dto, @MappingTarget ProductEntity entity);
}
