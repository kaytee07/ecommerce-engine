package com.shop.ecommerceengine.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO for the analytics dashboard combining all key metrics.
 * Cached in Redis with 10 minute TTL.
 */
public record AnalyticsDashboardDTO(
    // Revenue metrics
    BigDecimal todayRevenue,
    BigDecimal weekRevenue,
    BigDecimal monthRevenue,
    long todayOrderCount,
    long weekOrderCount,
    long monthOrderCount,

    // Sales funnel
    SalesFunnelDTO salesFunnel,

    // Top products (top 5)
    List<TopProductDTO> topProducts,

    // Low stock alerts (threshold < 5)
    List<LowStockProductDTO> lowStockAlerts,

    // Payment metrics
    long successfulPayments,
    long failedPayments,
    BigDecimal totalPaymentVolume,

    // Metadata
    Instant generatedAt
) implements Serializable {

    public AnalyticsDashboardDTO {
        if (todayRevenue == null) todayRevenue = BigDecimal.ZERO;
        if (weekRevenue == null) weekRevenue = BigDecimal.ZERO;
        if (monthRevenue == null) monthRevenue = BigDecimal.ZERO;
        if (totalPaymentVolume == null) totalPaymentVolume = BigDecimal.ZERO;
        if (generatedAt == null) generatedAt = Instant.now();
    }

    /**
     * Builder for constructing dashboard DTO.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BigDecimal todayRevenue = BigDecimal.ZERO;
        private BigDecimal weekRevenue = BigDecimal.ZERO;
        private BigDecimal monthRevenue = BigDecimal.ZERO;
        private long todayOrderCount = 0;
        private long weekOrderCount = 0;
        private long monthOrderCount = 0;
        private SalesFunnelDTO salesFunnel;
        private List<TopProductDTO> topProducts = List.of();
        private List<LowStockProductDTO> lowStockAlerts = List.of();
        private long successfulPayments = 0;
        private long failedPayments = 0;
        private BigDecimal totalPaymentVolume = BigDecimal.ZERO;
        private Instant generatedAt = Instant.now();

        public Builder todayRevenue(BigDecimal todayRevenue) {
            this.todayRevenue = todayRevenue;
            return this;
        }

        public Builder weekRevenue(BigDecimal weekRevenue) {
            this.weekRevenue = weekRevenue;
            return this;
        }

        public Builder monthRevenue(BigDecimal monthRevenue) {
            this.monthRevenue = monthRevenue;
            return this;
        }

        public Builder todayOrderCount(long todayOrderCount) {
            this.todayOrderCount = todayOrderCount;
            return this;
        }

        public Builder weekOrderCount(long weekOrderCount) {
            this.weekOrderCount = weekOrderCount;
            return this;
        }

        public Builder monthOrderCount(long monthOrderCount) {
            this.monthOrderCount = monthOrderCount;
            return this;
        }

        public Builder salesFunnel(SalesFunnelDTO salesFunnel) {
            this.salesFunnel = salesFunnel;
            return this;
        }

        public Builder topProducts(List<TopProductDTO> topProducts) {
            this.topProducts = topProducts;
            return this;
        }

        public Builder lowStockAlerts(List<LowStockProductDTO> lowStockAlerts) {
            this.lowStockAlerts = lowStockAlerts;
            return this;
        }

        public Builder successfulPayments(long successfulPayments) {
            this.successfulPayments = successfulPayments;
            return this;
        }

        public Builder failedPayments(long failedPayments) {
            this.failedPayments = failedPayments;
            return this;
        }

        public Builder totalPaymentVolume(BigDecimal totalPaymentVolume) {
            this.totalPaymentVolume = totalPaymentVolume;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public AnalyticsDashboardDTO build() {
            return new AnalyticsDashboardDTO(
                todayRevenue,
                weekRevenue,
                monthRevenue,
                todayOrderCount,
                weekOrderCount,
                monthOrderCount,
                salesFunnel,
                topProducts,
                lowStockAlerts,
                successfulPayments,
                failedPayments,
                totalPaymentVolume,
                generatedAt
            );
        }
    }
}
