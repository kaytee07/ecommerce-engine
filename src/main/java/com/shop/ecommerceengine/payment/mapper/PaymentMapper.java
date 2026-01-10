package com.shop.ecommerceengine.payment.mapper;

import com.shop.ecommerceengine.payment.dto.PaymentDTO;
import com.shop.ecommerceengine.payment.entity.PaymentEntity;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * MapStruct mapper for Payment entities and DTOs.
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentDTO toDTO(PaymentEntity entity);

    List<PaymentDTO> toDTOList(List<PaymentEntity> entities);
}
