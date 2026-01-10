package com.shop.ecommerceengine.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ecommerceengine.payment.gateway.HubtelPaymentClient;
import com.shop.ecommerceengine.payment.gateway.PaystackPaymentClient;
import com.shop.ecommerceengine.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for payment gateway webhooks.
 * These endpoints are public (no auth required) but verify signatures.
 */
@RestController
@RequestMapping("/webhook")
@Tag(name = "Webhooks", description = "Payment gateway webhook endpoints")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentService paymentService;
    private final HubtelPaymentClient hubtelClient;
    private final PaystackPaymentClient paystackClient;
    private final ObjectMapper objectMapper;

    public WebhookController(PaymentService paymentService,
                             HubtelPaymentClient hubtelClient,
                             PaystackPaymentClient paystackClient,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.hubtelClient = hubtelClient;
        this.paystackClient = paystackClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Hubtel webhook endpoint.
     * Verifies signature and processes payment status update.
     */
    @PostMapping("/hubtel")
    @Operation(summary = "Hubtel webhook", description = "Receives Hubtel payment notifications")
    public ResponseEntity<String> handleHubtelWebhook(
            @RequestHeader(value = "X-Hubtel-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        log.info("Received Hubtel webhook");

        try {
            // Validate signature
            if (!hubtelClient.validateWebhookSignature(rawPayload, signature)) {
                log.warn("Invalid Hubtel webhook signature");
                return ResponseEntity.status(401).body("Invalid signature");
            }

            // Parse payload
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawPayload, Map.class);

            String transactionRef = extractHubtelTransactionRef(payload);
            String status = extractHubtelStatus(payload);
            String message = extractHubtelMessage(payload);

            if (transactionRef == null) {
                log.warn("Hubtel webhook missing transaction reference");
                return ResponseEntity.badRequest().body("Missing transaction reference");
            }

            log.info("Processing Hubtel webhook: {} - Status: {}", transactionRef, status);

            paymentService.processHubtelWebhook(transactionRef, status, message);

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing Hubtel webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok("OK"); // Return OK to prevent retries for parse errors
        }
    }

    /**
     * Paystack webhook endpoint.
     * Verifies signature and processes payment status update.
     */
    @PostMapping("/paystack")
    @Operation(summary = "Paystack webhook", description = "Receives Paystack payment notifications")
    public ResponseEntity<String> handlePaystackWebhook(
            @RequestHeader(value = "X-Paystack-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        log.info("Received Paystack webhook");

        try {
            // Validate signature
            if (!paystackClient.validateWebhookSignature(rawPayload, signature)) {
                log.warn("Invalid Paystack webhook signature");
                return ResponseEntity.status(401).body("Invalid signature");
            }

            // Parse payload
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(rawPayload, Map.class);

            String event = (String) payload.get("event");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");

            if (data == null) {
                log.warn("Paystack webhook missing data");
                return ResponseEntity.badRequest().body("Missing data");
            }

            String reference = (String) data.get("reference");
            String status = (String) data.get("status");

            if (reference == null) {
                log.warn("Paystack webhook missing reference");
                return ResponseEntity.badRequest().body("Missing reference");
            }

            // Only process charge.success events
            if ("charge.success".equals(event)) {
                log.info("Processing Paystack webhook: {} - Status: {}", reference, status);
                paymentService.processPaystackWebhook(reference, status);
            } else {
                log.info("Ignoring Paystack event: {}", event);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("Error processing Paystack webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok("OK"); // Return OK to prevent retries for parse errors
        }
    }

    // ==================== Helper Methods ====================

    private String extractHubtelTransactionRef(Map<String, Object> payload) {
        // Try different possible locations
        if (payload.containsKey("Data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("Data");
            if (data.containsKey("CheckoutId")) {
                return (String) data.get("CheckoutId");
            }
            if (data.containsKey("ClientReference")) {
                return (String) data.get("ClientReference");
            }
        }
        if (payload.containsKey("checkoutId")) {
            return (String) payload.get("checkoutId");
        }
        if (payload.containsKey("clientReference")) {
            return (String) payload.get("clientReference");
        }
        if (payload.containsKey("reference")) {
            return (String) payload.get("reference");
        }
        return null;
    }

    private String extractHubtelStatus(Map<String, Object> payload) {
        if (payload.containsKey("Data")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("Data");
            if (data.containsKey("TransactionStatus")) {
                return (String) data.get("TransactionStatus");
            }
        }
        if (payload.containsKey("status")) {
            return (String) payload.get("status");
        }
        if (payload.containsKey("ResponseCode")) {
            String code = (String) payload.get("ResponseCode");
            return "0000".equals(code) ? "success" : "failed";
        }
        return "unknown";
    }

    private String extractHubtelMessage(Map<String, Object> payload) {
        if (payload.containsKey("Message")) {
            return (String) payload.get("Message");
        }
        if (payload.containsKey("message")) {
            return (String) payload.get("message");
        }
        return "";
    }
}
