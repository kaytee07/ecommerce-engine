package com.shop.ecommerceengine.identity.mapper;

import com.shop.ecommerceengine.identity.dto.TokenResponse;
import com.shop.ecommerceengine.identity.dto.UserInfoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for authentication DTOs.
 * Will be extended in Phase 3 for User entity mappings.
 */
@Mapper(componentModel = "spring")
public interface AuthMapper {

    /**
     * Create a token response from individual components.
     */
    @Mapping(target = "tokenType", constant = "Bearer")
    TokenResponse toTokenResponse(String accessToken, long expiresIn, String scope);

    /**
     * Map UserDetails to UserInfoResponse.
     * Note: In Phase 3, this will be replaced with User entity mapping.
     */
    default UserInfoResponse toUserInfoResponse(UserDetails userDetails) {
        return new UserInfoResponse(
                null, // ID will come from User entity in Phase 3
                userDetails.getUsername(),
                null, // Email will come from User entity in Phase 3
                mapAuthorities(userDetails.getAuthorities()),
                true  // emailVerified will come from User entity in Phase 3
        );
    }

    /**
     * Map Spring Security authorities to role strings.
     */
    default Set<String> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
