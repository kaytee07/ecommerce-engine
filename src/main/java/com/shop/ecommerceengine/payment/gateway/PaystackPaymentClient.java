package com.shop.ecommerceengine.payment.gateway;

import com.shop.ecommerceengine.payment.config.PaystackConfig;
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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Paystack payment gateway client (fallback gateway).
 * Uses WebClient for HTTP calls with circuit breaker protection.
 */
@Component
public class PaystackPaymentClient implements PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(PaystackPaymentClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "paystack";

    private final PaystackConfig config;
    private final WebClient webClient;

    public PaystackPaymentClient(PaystackConfig config) {
        this.config = config;
        this.webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getSecretKey())
                .build();
    }

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.PAYSTACK;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "initiatePaymentFallback")
    public PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request) {
        log.info("Initiating Paystack payment for reference: {}", request.clientReference());

        // Paystack expects amount in kobo (smallest currency unit)
        int amountInKobo = request.amount().multiply(BigDecimal.valueOf(100)).intValue();

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amountInKobo);
        payload.put("email", request.customerEmail() != null ? request.customerEmail() : "customer@example.com");
        payload.put("reference", request.clientReference());
        payload.put("callback_url", config.getCallbackUrl());
        payload.put("currency", request.currency() != null ? request.currency() : "GHS");

        if (request.metadata() != null) {
            payload.put("metadata", request.metadata());
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/transaction/initialize")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                String authorizationUrl = (String) data.get("authorization_url");
                String reference = (String) data.get("reference");

                log.info("Paystack payment initiated successfully: {}", reference);

                return new PaymentInitiationResponse(
                        true,
                        reference,
                        authorizationUrl,
                        null,
                        response
                );
            }

            String errorMessage = response != null ? (String) response.get("message") : "Unknown error";
            log.error("Paystack payment initiation failed: {}", errorMessage);

            return new PaymentInitiationResponse(false, null, null, errorMessage, response);

        } catch (Exception e) {
            log.error("Paystack payment initiation error: {}", e.getMessage(), e);
            throw new RuntimeException("Paystack payment initiation failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PaymentInitiationResponse initiatePaymentFallback(PaymentInitiationRequest request, Throwable t) {
        log.warn("Paystack circuit breaker fallback triggered for reference: {} - Error: {}",
                request.clientReference(), t.getMessage());
        return new PaymentInitiationResponse(false, null, null,
                "Paystack service unavailable: " + t.getMessage(), null);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "verifyPaymentFallback")
    public PaymentVerificationResponse verifyPayment(String transactionRef) {
        log.info("Verifying Paystack payment: {}", transactionRef);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.get()
                    .uri("/transaction/verify/{reference}", transactionRef)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                String status = (String) data.get("status");
                Number amountNum = (Number) data.get("amount");
                // Paystack returns amount in kobo
                BigDecimal amount = amountNum != null ?
                        BigDecimal.valueOf(amountNum.doubleValue()).divide(BigDecimal.valueOf(100)) :
                        BigDecimal.ZERO;
                String currency = (String) data.get("currency");

                log.info("Paystack payment verification: {} - Status: {}", transactionRef, status);

                return new PaymentVerificationResponse(
                        true,
                        transactionRef,
                        status,
                        amount,
                        currency != null ? currency : "GHS",
                        null,
                        response
                );
            }

            String errorMessage = response != null ? (String) response.get("message") : "Verification failed";
            return new PaymentVerificationResponse(false, transactionRef, "unknown", BigDecimal.ZERO, "GHS", errorMessage, response);

        } catch (Exception e) {
            log.error("Paystack payment verification error: {}", e.getMessage(), e);
            throw new RuntimeException("Paystack verification failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PaymentVerificationResponse verifyPaymentFallback(String transactionRef, Throwable t) {
        log.warn("Paystack verification circuit breaker fallback for: {} - Error: {}", transactionRef, t.getMessage());
        return new PaymentVerificationResponse(false, transactionRef, "unknown", BigDecimal.ZERO, "GHS",
                "Paystack service unavailable: " + t.getMessage(), null);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "refundPaymentFallback")
    public RefundResponse refundPayment(String transactionRef, BigDecimal amount, String reason) {
        log.info("Processing Paystack refund for: {} - Amount: {}", transactionRef, amount);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction", transactionRef);
        // Paystack expects amount in kobo
        payload.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri("/refund")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                String refundId = data != null ? String.valueOf(data.get("id")) : transactionRef + "-REFUND";

                log.info("Paystack refund processed: {}", refundId);

                return new RefundResponse(true, refundId, null, response);
            }

            String errorMessage = response != null ? (String) response.get("message") : "Refund failed";
            return new RefundResponse(false, null, errorMessage, response);

        } catch (Exception e) {
            log.error("Paystack refund error: {}", e.getMessage(), e);
            throw new RuntimeException("Paystack refund failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private RefundResponse refundPaymentFallback(String transactionRef, BigDecimal amount, String reason, Throwable t) {
        log.warn("Paystack refund circuit breaker fallback for: {} - Error: {}", transactionRef, t.getMessage());
        return new RefundResponse(false, null, "Paystack service unavailable: " + t.getMessage(), null);
    }

    @Override
    public boolean validateWebhookSignature(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            log.warn("Missing Paystack webhook signature");
            return false;
        }

        try {
            Mac sha512HMAC = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(
                    config.getWebhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512"
            );
            sha512HMAC.init(secretKey);
            byte[] hash = sha512HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(hash);

            boolean valid = computedSignature.equalsIgnoreCase(signature);
            if (!valid) {
                log.warn("Invalid Paystack webhook signature");
            }
            return valid;

        } catch (Exception e) {
            log.error("Error validating Paystack webhook signature: {}", e.getMessage());
            return false;
        }
    }
}
