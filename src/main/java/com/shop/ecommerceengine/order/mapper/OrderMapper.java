package com.shop.ecommerceengine.order.mapper;

import com.shop.ecommerceengine.order.dto.OrderDTO;
import com.shop.ecommerceengine.order.dto.OrderHistoryDTO;
import com.shop.ecommerceengine.order.dto.OrderItemDTO;
import com.shop.ecommerceengine.order.entity.OrderEntity;
import com.shop.ecommerceengine.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * MapStruct mapper for Order entities and DTOs.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    /**
     * Map OrderEntity to full OrderDTO.
     */
    @Mapping(target = "itemCount", expression = "java(entity.getItemCount())")
    @Mapping(target = "items", source = "items", qualifiedByName = "mapItems")
    OrderDTO toDTO(OrderEntity entity);

    /**
     * Map OrderEntity to OrderHistoryDTO (summary).
     */
    @Mapping(target = "itemCount", expression = "java(entity.getItemCount())")
    @Mapping(target = "statusDisplayName", expression = "java(entity.getStatus().getDisplayName())")
    OrderHistoryDTO toHistoryDTO(OrderEntity entity);

    /**
     * Map list of entities to list of OrderDTO.
     */
    List<OrderDTO> toDTOList(List<OrderEntity> entities);

    /**
     * Map list of entities to list of OrderHistoryDTO.
     */
    List<OrderHistoryDTO> toHistoryDTOList(List<OrderEntity> entities);

    /**
     * Map OrderItem to OrderItemDTO.
     */
    @Mapping(target = "subtotal", expression = "java(item.getSubtotal())")
    OrderItemDTO toItemDTO(OrderItem item);

    /**
     * Map list of OrderItem to list of OrderItemDTO.
     */
    @Named("mapItems")
    List<OrderItemDTO> toItemDTOList(List<OrderItem> items);
}
