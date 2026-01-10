package com.shop.ecommerceengine.payment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.shop.ecommerceengine.order.entity.OrderEntity;
import com.shop.ecommerceengine.order.entity.OrderItem;
import com.shop.ecommerceengine.order.entity.OrderStatus;
import com.shop.ecommerceengine.order.repository.OrderRepository;
import com.shop.ecommerceengine.payment.dto.PaymentDTO;
import com.shop.ecommerceengine.payment.dto.PaymentInitiateDTO;
import com.shop.ecommerceengine.payment.entity.PaymentEntity;
import com.shop.ecommerceengine.payment.entity.PaymentGateway;
import com.shop.ecommerceengine.payment.entity.PaymentStatus;
import com.shop.ecommerceengine.payment.exception.IdempotencyKeyViolationException;
import com.shop.ecommerceengine.payment.exception.PaymentFailedException;
import com.shop.ecommerceengine.payment.repository.PaymentRepository;
import com.shop.ecommerceengine.payment.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Payment service.
 * Uses WireMock to mock Hubtel and Paystack API responses.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentServiceIntegrationTest {

    private static RedisServer redisServer;
    private static WireMockServer hubtelMockServer;
    private static WireMockServer paystackMockServer;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    private UUID testOrderId;
    private UUID testUserId;

    @BeforeAll
    static void setupServers() throws IOException {
        // Start embedded Redis
        redisServer = new RedisServer(6370);
        redisServer.start();

        // Start WireMock servers for payment gateways
        hubtelMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8089));
        hubtelMockServer.start();
        WireMock.configureFor("localhost", 8089);

        paystackMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8090));
        paystackMockServer.start();
    }

    @AfterAll
    static void tearDownServers() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
        if (hubtelMockServer != null) {
            hubtelMockServer.stop();
        }
        if (paystackMockServer != null) {
            paystackMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.port", () -> "6370");

        // Configure Hubtel mock
        registry.add("payment.hubtel.enabled", () -> "true");
        registry.add("payment.hubtel.base-url", () -> "http://localhost:8089");
        registry.add("payment.hubtel.client-id", () -> "test-client-id");
        registry.add("payment.hubtel.client-secret", () -> "test-client-secret");
        registry.add("payment.hubtel.merchant-account", () -> "test-merchant");
        registry.add("payment.hubtel.callback-url", () -> "http://localhost:8080/webhook/hubtel");
        registry.add("payment.hubtel.webhook-secret", () -> "hubtel-webhook-secret");

        // Configure Paystack mock
        registry.add("payment.paystack.enabled", () -> "true");
        registry.add("payment.paystack.base-url", () -> "http://localhost:8090");
        registry.add("payment.paystack.secret-key", () -> "sk_test_xxx");
        registry.add("payment.paystack.callback-url", () -> "http://localhost:8080/webhook/paystack");
        registry.add("payment.paystack.webhook-secret", () -> "paystack-webhook-secret");
    }

    @BeforeEach
    void setUp() {
        // Reset WireMock stubs
        hubtelMockServer.resetAll();
        paystackMockServer.resetAll();

        // Create test order
        testUserId = UUID.randomUUID();
        OrderEntity order = new OrderEntity(testUserId);
        order.setItemsAndRecalculate(List.of(
                new OrderItem(UUID.randomUUID(), "Test Product", "SKU-001", 2, new BigDecimal("50.00"))
        ));
        order.setStatus(OrderStatus.CONFIRMED);
        order = orderRepository.save(order);
        testOrderId = order.getId();
    }

    @AfterEach
    void cleanUp() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    // ==================== Payment Initiation Tests ====================

    @Test
    @Order(1)
    @DisplayName("initiatePayment with Hubtel success returns checkout URL")
    void initiatePayment_hubtelSuccess_returnsCheckoutUrl() {
        // Mock Hubtel success response
        stubHubtelInitiateSuccess("TXN-12345", "https://hubtel.com/checkout/12345");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentInitiateDTO request = new PaymentInitiateDTO(testOrderId, idempotencyKey, null);

        PaymentDTO result = paymentService.initiatePayment(request, testUserId);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.gateway()).isEqualTo(PaymentGateway.HUBTEL);
        assertThat(result.checkoutUrl()).isEqualTo("https://hubtel.com/checkout/12345");
        assertThat(result.transactionRef()).isEqualTo("TXN-12345");
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @Order(2)
    @DisplayName("initiatePayment falls back to Paystack when Hubtel fails")
    void initiatePayment_hubtelFails_fallsBackToPaystack() {
        // Mock Hubtel failure
        stubHubtelInitiateFailure();
        // Mock Paystack success
        stubPaystackInitiateSuccess("PAY-67890", "https://paystack.com/checkout/67890");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentInitiateDTO request = new PaymentInitiateDTO(testOrderId, idempotencyKey, null);

        PaymentDTO result = paymentService.initiatePayment(request, testUserId);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.gateway()).isEqualTo(PaymentGateway.PAYSTACK);
        assertThat(result.checkoutUrl()).isEqualTo("https://paystack.com/checkout/67890");
    }

    @Test
    @Order(3)
    @DisplayName("initiatePayment throws when both gateways fail")
    void initiatePayment_bothGatewaysFail_throwsException() {
        // Mock both gateways failing
        stubHubtelInitiateFailure();
        stubPaystackInitiateFailure();

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentInitiateDTO request = new PaymentInitiateDTO(testOrderId, idempotencyKey, null);

        assertThatThrownBy(() -> paymentService.initiatePayment(request, testUserId))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Payment failed for order");
    }

    // ==================== Idempotency Tests ====================

    @Test
    @Order(10)
    @DisplayName("initiatePayment with duplicate idempotencyKey returns existing payment")
    void initiatePayment_duplicateKey_returnsExistingPayment() {
        // Mock Hubtel success
        stubHubtelInitiateSuccess("TXN-FIRST", "https://hubtel.com/checkout/first");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentInitiateDTO request = new PaymentInitiateDTO(testOrderId, idempotencyKey, null);

        // First call
        PaymentDTO first = paymentService.initiatePayment(request, testUserId);
        assertThat(first.transactionRef()).isEqualTo("TXN-FIRST");

        // Second call with same idempotency key - should return existing payment
        PaymentDTO second = paymentService.initiatePayment(request, testUserId);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.transactionRef()).isEqualTo("TXN-FIRST");
    }

    @Test
    @Order(11)
    @DisplayName("initiatePayment with same idempotencyKey but different orderId throws")
    void initiatePayment_sameKeyDifferentOrder_throwsException() {
        // Create second order
        OrderEntity secondOrder = new OrderEntity(testUserId);
        secondOrder.setItemsAndRecalculate(List.of(
                new OrderItem(UUID.randomUUID(), "Other Product", "SKU-002", 1, new BigDecimal("75.00"))
        ));
        secondOrder.setStatus(OrderStatus.CONFIRMED);
        secondOrder = orderRepository.save(secondOrder);

        // Mock Hubtel success
        stubHubtelInitiateSuccess("TXN-ORIG", "https://hubtel.com/checkout/orig");

        String idempotencyKey = UUID.randomUUID().toString();

        // First payment
        PaymentInitiateDTO first = new PaymentInitiateDTO(testOrderId, idempotencyKey, null);
        paymentService.initiatePayment(first, testUserId);

        // Second payment with same key but different order
        PaymentInitiateDTO second = new PaymentInitiateDTO(secondOrder.getId(), idempotencyKey, null);

        assertThatThrownBy(() -> paymentService.initiatePayment(second, testUserId))
                .isInstanceOf(IdempotencyKeyViolationException.class)
                .hasMessageContaining("Idempotency key already used");
    }

    // ==================== Payment Verification Tests ====================

    @Test
    @Order(20)
    @DisplayName("verifyPayment updates status to SUCCESS when gateway confirms")
    void verifyPayment_gatewayConfirms_updatesStatusToSuccess() {
        // Setup: Create a pending payment
        stubHubtelInitiateSuccess("TXN-VERIFY", "https://hubtel.com/checkout/verify");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentDTO initiated = paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        // Mock verification success
        stubHubtelVerifySuccess("TXN-VERIFY", "success");

        // Verify
        PaymentDTO verified = paymentService.verifyPayment("TXN-VERIFY");

        assertThat(verified.status()).isEqualTo(PaymentStatus.SUCCESS);

        // Check order status updated
        OrderEntity order = orderRepository.findById(testOrderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    @Order(21)
    @DisplayName("verifyPayment updates status to FAILED when gateway reports failure")
    void verifyPayment_gatewayFails_updatesStatusToFailed() {
        // Setup: Create a pending payment
        stubHubtelInitiateSuccess("TXN-FAIL", "https://hubtel.com/checkout/fail");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentDTO initiated = paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        // Mock verification failure
        stubHubtelVerifySuccess("TXN-FAIL", "failed");

        // Verify
        PaymentDTO verified = paymentService.verifyPayment("TXN-FAIL");

        assertThat(verified.status()).isEqualTo(PaymentStatus.FAILED);
    }

    // ==================== Webhook Tests ====================

    @Test
    @Order(30)
    @DisplayName("processHubtelWebhook updates payment status")
    void processHubtelWebhook_validPayload_updatesPayment() {
        // Setup: Create a pending payment
        stubHubtelInitiateSuccess("TXN-WEBHOOK", "https://hubtel.com/checkout/webhook");

        String idempotencyKey = UUID.randomUUID().toString();
        paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        // Mock verification for webhook processing
        stubHubtelVerifySuccess("TXN-WEBHOOK", "success");

        // Process webhook
        paymentService.processHubtelWebhook("TXN-WEBHOOK", "success", "completed");

        // Verify payment status
        Optional<PaymentEntity> payment = paymentRepository.findByTransactionRef("TXN-WEBHOOK");
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @Order(31)
    @DisplayName("processPaystackWebhook updates payment status")
    void processPaystackWebhook_validPayload_updatesPayment() {
        // First, make Hubtel fail so payment goes through Paystack
        stubHubtelInitiateFailure();
        stubPaystackInitiateSuccess("PAY-WEBHOOK", "https://paystack.com/checkout/webhook");

        String idempotencyKey = UUID.randomUUID().toString();
        paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        // Mock Paystack verification
        stubPaystackVerifySuccess("PAY-WEBHOOK", "success");

        // Process webhook
        paymentService.processPaystackWebhook("PAY-WEBHOOK", "success");

        // Verify payment status
        Optional<PaymentEntity> payment = paymentRepository.findByTransactionRef("PAY-WEBHOOK");
        assertThat(payment).isPresent();
        assertThat(payment.get().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    // ==================== Refund Tests ====================

    @Test
    @Order(40)
    @DisplayName("refundPayment updates status to REFUNDED")
    void refundPayment_successfulPayment_updatesStatusToRefunded() {
        // Setup: Create and complete a payment
        stubHubtelInitiateSuccess("TXN-REFUND", "https://hubtel.com/checkout/refund");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentDTO initiated = paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        // Mark as SUCCESS (simulate webhook)
        stubHubtelVerifySuccess("TXN-REFUND", "success");
        paymentService.verifyPayment("TXN-REFUND");

        // Mock refund success
        stubHubtelRefundSuccess("TXN-REFUND");

        // Refund
        UUID adminId = UUID.randomUUID();
        PaymentDTO refunded = paymentService.refundPayment(initiated.id(), adminId, "Customer requested refund");

        assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);

        // Verify order status updated
        OrderEntity order = orderRepository.findById(testOrderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @Order(41)
    @DisplayName("refundPayment throws for non-SUCCESS payment")
    void refundPayment_pendingPayment_throwsException() {
        // Setup: Create a pending payment (not completed)
        stubHubtelInitiateSuccess("TXN-PENDING", "https://hubtel.com/checkout/pending");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentDTO initiated = paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        UUID adminId = UUID.randomUUID();
        assertThatThrownBy(() -> paymentService.refundPayment(initiated.id(), adminId, "Test refund"))
                .isInstanceOf(PaymentFailedException.class)
                .hasMessageContaining("Cannot refund payment");
    }

    // ==================== Soft Delete Tests ====================

    @Test
    @Order(50)
    @DisplayName("softDeletePayment sets deletedAt timestamp")
    void softDeletePayment_setsDeletedAt() {
        // Setup payment
        stubHubtelInitiateSuccess("TXN-DELETE", "https://hubtel.com/checkout/delete");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentDTO initiated = paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        // Soft delete
        paymentService.softDeletePayment(initiated.id());

        // Verify
        Optional<PaymentEntity> deleted = paymentRepository.findById(initiated.id());
        assertThat(deleted).isPresent();
        assertThat(deleted.get().getDeletedAt()).isNotNull();
    }

    // ==================== Query Tests ====================

    @Test
    @Order(60)
    @DisplayName("getPaymentsByOrderId returns payments for order")
    void getPaymentsByOrderId_returnsPayments() {
        stubHubtelInitiateSuccess("TXN-QUERY", "https://hubtel.com/checkout/query");

        String idempotencyKey = UUID.randomUUID().toString();
        paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        List<PaymentDTO> payments = paymentService.getPaymentsByOrderId(testOrderId);

        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).orderId()).isEqualTo(testOrderId);
    }

    @Test
    @Order(61)
    @DisplayName("getPaymentById returns payment")
    void getPaymentById_existingPayment_returnsPayment() {
        stubHubtelInitiateSuccess("TXN-GET", "https://hubtel.com/checkout/get");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentDTO created = paymentService.initiatePayment(
                new PaymentInitiateDTO(testOrderId, idempotencyKey, null),
                testUserId
        );

        PaymentDTO found = paymentService.getPaymentById(created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.transactionRef()).isEqualTo("TXN-GET");
    }

    // ==================== WireMock Stub Helpers ====================

    private void stubHubtelInitiateSuccess(String transactionRef, String checkoutUrl) {
        hubtelMockServer.stubFor(post(urlPathMatching("/api/v1/merchant/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "status": "Success",
                                "data": {
                                    "checkoutUrl": "%s",
                                    "checkoutId": "%s",
                                    "clientReference": "%s"
                                }
                            }
                            """.formatted(checkoutUrl, transactionRef, transactionRef))));
    }

    private void stubHubtelInitiateFailure() {
        hubtelMockServer.stubFor(post(urlPathMatching("/api/v1/merchant/.*"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "status": "Error",
                                "message": "Service unavailable"
                            }
                            """)));
    }

    private void stubHubtelVerifySuccess(String transactionRef, String status) {
        hubtelMockServer.stubFor(get(urlPathMatching("/api/v1/merchant/.*/status/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "status": "Success",
                                "data": {
                                    "checkoutId": "%s",
                                    "transactionStatus": "%s",
                                    "amount": 100.00
                                }
                            }
                            """.formatted(transactionRef, status))));
    }

    private void stubHubtelRefundSuccess(String transactionRef) {
        hubtelMockServer.stubFor(post(urlPathMatching("/api/v1/merchant/.*/refund"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "status": "Success",
                                "data": {
                                    "refundId": "REF-%s",
                                    "status": "completed"
                                }
                            }
                            """.formatted(transactionRef))));
    }

    private void stubPaystackInitiateSuccess(String transactionRef, String checkoutUrl) {
        paystackMockServer.stubFor(post(urlEqualTo("/transaction/initialize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "status": true,
                                "message": "Authorization URL created",
                                "data": {
                                    "authorization_url": "%s",
                                    "access_code": "ACCESS_%s",
                                    "reference": "%s"
                                }
                            }
                            """.formatted(checkoutUrl, transactionRef, transactionRef))));
    }

    private void stubPaystackInitiateFailure() {
        paystackMockServer.stubFor(post(urlEqualTo("/transaction/initialize"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "status": false,
                                "message": "Service unavailable"
                            }
                            """)));
    }

    private void stubPaystackVerifySuccess(String reference, String status) {
        paystackMockServer.stubFor(get(urlEqualTo("/transaction/verify/" + reference))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "status": true,
                                "data": {
                                    "reference": "%s",
                                    "status": "%s",
                                    "amount": 10000
                                }
                            }
                            """.formatted(reference, status))));
    }
}
