package com.shop.ecommerceengine.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * DTO for sales funnel metrics (cart → order → payment conversion).
 */
public record SalesFunnelDTO(
    long cartCount,
    long orderCount,
    long paidOrderCount,
    long failedPaymentCount,
    BigDecimal cartToOrderRate,
    BigDecimal orderToPaidRate
) implements Serializable {

    public SalesFunnelDTO {
        if (cartToOrderRate == null) cartToOrderRate = BigDecimal.ZERO;
        if (orderToPaidRate == null) orderToPaidRate = BigDecimal.ZERO;
    }

    /**
     * Calculate conversion rates from raw counts.
     */
    public static SalesFunnelDTO fromCounts(long cartCount, long orderCount, long paidOrderCount, long failedPaymentCount) {
        BigDecimal cartToOrderRate = BigDecimal.ZERO;
        BigDecimal orderToPaidRate = BigDecimal.ZERO;

        if (cartCount > 0) {
            cartToOrderRate = BigDecimal.valueOf(orderCount)
                .divide(BigDecimal.valueOf(cartCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        if (orderCount > 0) {
            orderToPaidRate = BigDecimal.valueOf(paidOrderCount)
                .divide(BigDecimal.valueOf(orderCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        return new SalesFunnelDTO(
            cartCount,
            orderCount,
            paidOrderCount,
            failedPaymentCount,
            cartToOrderRate,
            orderToPaidRate
        );
    }
}
