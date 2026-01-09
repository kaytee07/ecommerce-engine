package com.shop.ecommerceengine.inventory.mapper;

import com.shop.ecommerceengine.inventory.dto.InventoryAdminDTO;
import com.shop.ecommerceengine.inventory.dto.InventoryDTO;
import com.shop.ecommerceengine.inventory.entity.InventoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for Inventory entity and DTOs.
 */
@Mapper(componentModel = "spring")
public interface InventoryMapper {

    /**
     * Maps InventoryEntity to InventoryDTO for storefront.
     */
    @Mapping(target = "availableQuantity", expression = "java(entity.getAvailableQuantity())")
    InventoryDTO toDTO(InventoryEntity entity);

    /**
     * Maps a list of InventoryEntity to a list of InventoryDTO.
     */
    List<InventoryDTO> toDTOList(List<InventoryEntity> entities);

    /**
     * Maps InventoryEntity to InventoryAdminDTO with full admin fields.
     */
    @Mapping(target = "availableQuantity", expression = "java(entity.getAvailableQuantity())")
    InventoryAdminDTO toAdminDTO(InventoryEntity entity);

    /**
     * Maps a list of InventoryEntity to a list of InventoryAdminDTO.
     */
    List<InventoryAdminDTO> toAdminDTOList(List<InventoryEntity> entities);
}
