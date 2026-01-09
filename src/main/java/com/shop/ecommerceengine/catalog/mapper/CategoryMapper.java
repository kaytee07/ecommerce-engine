package com.shop.ecommerceengine.catalog.mapper;

import com.shop.ecommerceengine.catalog.dto.CategoryCreateDTO;
import com.shop.ecommerceengine.catalog.dto.CategoryDTO;
import com.shop.ecommerceengine.catalog.dto.CategoryTreeDTO;
import com.shop.ecommerceengine.catalog.entity.CategoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * MapStruct mapper for Category entity and DTOs.
 * Uses Spring component model for dependency injection.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CategoryMapper {

    /**
     * Maps CategoryEntity to CategoryDTO.
     * Parent name is mapped from the associated parent category if present.
     */
    @Mapping(target = "parentName", source = "parent.name")
    CategoryDTO toDTO(CategoryEntity entity);

    /**
     * Maps a list of CategoryEntity to a list of CategoryDTO.
     */
    List<CategoryDTO> toDTOList(List<CategoryEntity> entities);

    /**
     * Maps CategoryEntity to CategoryTreeDTO for hierarchical display.
     * Children are recursively mapped.
     */
    @Mapping(target = "children", source = "children")
    CategoryTreeDTO toTreeDTO(CategoryEntity entity);

    /**
     * Maps a list of CategoryEntity to a list of CategoryTreeDTO.
     */
    List<CategoryTreeDTO> toTreeDTOList(List<CategoryEntity> entities);

    /**
     * Maps CategoryCreateDTO to CategoryEntity for creation.
     * ID and timestamps are managed by JPA.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "slug", expression = "java(dto.effectiveSlug())")
    @Mapping(target = "displayOrder", expression = "java(dto.effectiveDisplayOrder())")
    @Mapping(target = "active", expression = "java(dto.effectiveActive())")
    CategoryEntity toEntity(CategoryCreateDTO dto);

    /**
     * Updates an existing CategoryEntity from a CategoryCreateDTO.
     * Null values in DTO are ignored.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "children", ignore = true)
    void updateEntity(CategoryCreateDTO dto, @MappingTarget CategoryEntity entity);
}
