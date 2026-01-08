package com.shop.ecommerceengine.identity.mapper;

import com.shop.ecommerceengine.identity.dto.UserCreateDTO;
import com.shop.ecommerceengine.identity.dto.UserDTO;
import com.shop.ecommerceengine.identity.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * MapStruct mapper for User entity and DTOs.
 * Ignores password when mapping to DTO.
 */
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    /**
     * Convert UserEntity to UserDTO.
     * Password is not included in the DTO.
     */
    UserDTO toDto(UserEntity entity);

    /**
     * Convert list of UserEntity to list of UserDTO.
     */
    List<UserDTO> toDtoList(List<UserEntity> entities);

    /**
     * Convert UserCreateDTO to UserEntity.
     * Password is set separately after encoding.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "enabled", constant = "true")
    @Mapping(target = "locked", constant = "false")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserEntity toEntity(UserCreateDTO dto);

    /**
     * Update existing UserEntity with new data.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "locked", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget UserEntity entity, UserDTO dto);
}
