package com.shop.ecommerceengine.cart.mapper;

import com.shop.ecommerceengine.cart.dto.CartDTO;
import com.shop.ecommerceengine.cart.dto.CartItemDTO;
import com.shop.ecommerceengine.cart.entity.CartEntity;
import com.shop.ecommerceengine.cart.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for Cart entity and DTOs.
 */
@Mapper(componentModel = "spring")
public interface CartMapper {

    /**
     * Maps CartItem to CartItemDTO.
     */
    @Mapping(target = "subtotal", expression = "java(item.getSubtotal())")
    CartItemDTO toItemDTO(CartItem item);

    /**
     * Maps list of CartItem to list of CartItemDTO.
     */
    List<CartItemDTO> toItemDTOList(List<CartItem> items);

    /**
     * Maps CartEntity to CartDTO.
     */
    @Mapping(target = "items", source = "items")
    @Mapping(target = "itemCount", expression = "java(entity.getItemCount())")
    @Mapping(target = "totalAmount", expression = "java(entity.getTotalAmount())")
    CartDTO toDTO(CartEntity entity);
}
