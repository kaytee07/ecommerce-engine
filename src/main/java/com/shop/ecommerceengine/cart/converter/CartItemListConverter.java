package com.shop.ecommerceengine.cart.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shop.ecommerceengine.cart.entity.CartItem;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA converter for List<CartItem> to JSONB.
 * Used for storing cart items in PostgreSQL.
 */
@Converter
public class CartItemListConverter implements AttributeConverter<List<CartItem>, String> {

    private static final Logger log = LoggerFactory.getLogger(CartItemListConverter.class);
    private static final ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String convertToDatabaseColumn(List<CartItem> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting CartItem list to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    @Override
    public List<CartItem> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty() || "[]".equals(dbData)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<CartItem>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to CartItem list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
