package com.shop.ecommerceengine.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for daily sales statistics from materialized view.
 */
public record DailySalesDTO(
    LocalDate saleDate,
    long orderCount,
    BigDecimal totalRevenue,
    BigDecimal avgOrderValue,
    long uniqueCustomers
) implements Serializable {

    public DailySalesDTO {
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        if (avgOrderValue == null) avgOrderValue = BigDecimal.ZERO;
    }
}
