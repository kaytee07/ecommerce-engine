package com.shop.ecommerceengine.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for top selling products from materialized view.
 */
public record TopProductDTO(
    UUID productId,
    String productName,
    long totalSold,
    BigDecimal totalRevenue,
    long orderCount
) implements Serializable {

    public TopProductDTO {
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
    }
}
