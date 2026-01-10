package com.shop.ecommerceengine.payment.gateway;

import com.shop.ecommerceengine.payment.config.HubtelConfig;
import com.shop.ecommerceengine.payment.entity.PaymentGateway;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Hubtel payment gateway client.
 * Uses WebClient for HTTP calls with circuit breaker protection.
 */
@Component
public class HubtelPaymentClient implements PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(HubtelPaymentClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "hubtel";

    private final HubtelConfig config;
    private final WebClient webClient;

    public HubtelPaymentClient(HubtelConfig config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, createBasicAuth())
                .build();
    }

    private String createBasicAuth() {
        String credentials = config.getClientId() + ":" + config.getClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.HUBTEL;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "initiatePaymentFallback")
    public PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request) {
        log.info("Initiating Hubtel payment for reference: {}", request.clientReference());

        Map<String, Object> payload = new HashMap<>();
        payload.put("totalAmount", request.amount().doubleValue());
        payload.put("description", request.description());
        payload.put("callbackUrl", config.getCallbackUrl());
        payload.put("returnUrl", config.getReturnUrl());
        payload.put("merchantBusinessLogoUrl", "");
        payload.put("merchantAccountNumber", config.getMerchantAccount());
        payload.put("cancellationUrl", config.getReturnUrl());
        payload.put("clientReference", request.clientReference());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/api/v1/merchant/{merchantAccount}/receive/mobilemoney", config.getMerchantAccount())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && "Success".equalsIgnoreCase((String) response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                String checkoutUrl = (String) data.get("checkoutUrl");
                String checkoutId = (String) data.get("checkoutId");

                log.info("Hubtel payment initiated successfully: {}", checkoutId);

                return new PaymentInitiationResponse(
                        true,
                        checkoutId,
                        checkoutUrl,
                        null,
                        response
                );
            }

            String errorMessage = response != null ? (String) response.get("message") : "Unknown error";
            log.error("Hubtel payment initiation failed: {}", errorMessage);

            return new PaymentInitiationResponse(false, null, null, errorMessage, response);

        } catch (Exception e) {
            log.error("Hubtel payment initiation error: {}", e.getMessage(), e);
            throw new RuntimeException("Hubtel payment initiation failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PaymentInitiationResponse initiatePaymentFallback(PaymentInitiationRequest request, Throwable t) {
        log.warn("Hubtel circuit breaker fallback triggered for reference: {} - Error: {}",
                request.clientReference(), t.getMessage());
        return new PaymentInitiationResponse(false, null, null,
                "Hubtel service unavailable: " + t.getMessage(), null);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "verifyPaymentFallback")
    public PaymentVerificationResponse verifyPayment(String transactionRef) {
        log.info("Verifying Hubtel payment: {}", transactionRef);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/api/v1/merchant/{merchantAccount}/status/{transactionRef}",
                            config.getMerchantAccount(), transactionRef)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && "Success".equalsIgnoreCase((String) response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                String status = (String) data.get("transactionStatus");
                Number amountNum = (Number) data.get("amount");
                BigDecimal amount = amountNum != null ? BigDecimal.valueOf(amountNum.doubleValue()) : BigDecimal.ZERO;

                log.info("Hubtel payment verification: {} - Status: {}", transactionRef, status);

                return new PaymentVerificationResponse(
                        true,
                        transactionRef,
                        status,
                        amount,
                        "GHS",
                        null,
                        response
                );
            }

            String errorMessage = response != null ? (String) response.get("message") : "Verification failed";
            return new PaymentVerificationResponse(false, transactionRef, "unknown", BigDecimal.ZERO, "GHS", errorMessage, response);

        } catch (Exception e) {
            log.error("Hubtel payment verification error: {}", e.getMessage(), e);
            throw new RuntimeException("Hubtel verification failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PaymentVerificationResponse verifyPaymentFallback(String transactionRef, Throwable t) {
        log.warn("Hubtel verification circuit breaker fallback for: {} - Error: {}", transactionRef, t.getMessage());
        return new PaymentVerificationResponse(false, transactionRef, "unknown", BigDecimal.ZERO, "GHS",
                "Hubtel service unavailable: " + t.getMessage(), null);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "refundPaymentFallback")
    public RefundResponse refundPayment(String transactionRef, BigDecimal amount, String reason) {
        log.info("Processing Hubtel refund for: {} - Amount: {}", transactionRef, amount);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", transactionRef);
        payload.put("amount", amount.doubleValue());
        payload.put("reason", reason);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/api/v1/merchant/{merchantAccount}/refund", config.getMerchantAccount())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && "Success".equalsIgnoreCase((String) response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                String refundId = data != null ? (String) data.get("refundId") : transactionRef + "-REFUND";

                log.info("Hubtel refund processed: {}", refundId);

                return new RefundResponse(true, refundId, null, response);
            }

            String errorMessage = response != null ? (String) response.get("message") : "Refund failed";
            return new RefundResponse(false, null, errorMessage, response);

        } catch (Exception e) {
            log.error("Hubtel refund error: {}", e.getMessage(), e);
            throw new RuntimeException("Hubtel refund failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private RefundResponse refundPaymentFallback(String transactionRef, BigDecimal amount, String reason, Throwable t) {
        log.warn("Hubtel refund circuit breaker fallback for: {} - Error: {}", transactionRef, t.getMessage());
        return new RefundResponse(false, null, "Hubtel service unavailable: " + t.getMessage(), null);
    }

    @Override
    public boolean validateWebhookSignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            log.warn("Missing Hubtel webhook signature");
            return false;
        }

        try {
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    config.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            sha256HMAC.init(secretKey);
            String computedSignature = Base64.getEncoder().encodeToString(
                    sha256HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );

            boolean valid = computedSignature.equals(signature);
            if (!valid) {
                log.warn("Invalid Hubtel webhook signature");
            }
            return valid;

        } catch (Exception e) {
            log.error("Error validating Hubtel webhook signature: {}", e.getMessage());
            return false;
        }
    }
}
