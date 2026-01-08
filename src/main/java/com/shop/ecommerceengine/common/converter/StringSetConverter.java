package com.shop.ecommerceengine.common.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * JPA converter for Set<String> to JSONB.
 * Used for storing roles as JSON array in PostgreSQL.
 */
@Converter(autoApply = false)
public class StringSetConverter implements AttributeConverter<Set<String>, String> {

    private static final Logger log = LoggerFactory.getLogger(StringSetConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Error converting Set to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashSet<>();
        }
        try {
            // Handle H2's PostgreSQL mode which may double-escape the JSON
            String jsonData = dbData.trim();

            // If the data is wrapped in extra quotes (double-serialized), unwrap it
            if (jsonData.startsWith("\"") && jsonData.endsWith("\"") && jsonData.length() > 2) {
                jsonData = objectMapper.readValue(jsonData, String.class);
            }

            return objectMapper.readValue(jsonData, new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to Set. Data: '{}', Error: {}", dbData, e.getMessage());
            return new HashSet<>();
        }
    }
}
