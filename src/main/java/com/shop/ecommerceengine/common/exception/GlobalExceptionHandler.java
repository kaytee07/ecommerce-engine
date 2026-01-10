package com.shop.ecommerceengine.common.exception;

import com.shop.ecommerceengine.common.dto.ApiError;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.identity.exception.AuthException;
import com.shop.ecommerceengine.order.exception.InvalidOrderStateException;
import com.shop.ecommerceengine.order.exception.OrderNotFoundException;
import com.shop.ecommerceengine.payment.exception.IdempotencyKeyViolationException;
import com.shop.ecommerceengine.payment.exception.PaymentFailedException;
import com.shop.ecommerceengine.payment.exception.PaymentNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that catches all exceptions and returns standardized ApiResponse with ApiError.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleAuthException(
            AuthException ex, WebRequest request) {

        log.warn("Authentication exception: {} [errorCode={}]", ex.getMessage(), ex.getErrorCode());

        ApiError apiError = new ApiError(
                ex.getHttpStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                ex.getDetails()
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleSpringAuthenticationException(
            AuthenticationException ex, WebRequest request) {

        log.warn("Spring authentication exception: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                HttpStatus.UNAUTHORIZED.value(),
                "Authentication failed",
                "AUTHENTICATION_FAILED"
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, "Authentication failed");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {

        log.warn("Access denied: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                HttpStatus.FORBIDDEN.value(),
                "Access denied",
                "ACCESS_DENIED"
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, "Access denied");
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleRateLimitExceededException(
            RateLimitExceededException ex, WebRequest request) {

        log.warn("Rate limit exceeded: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ex.getMessage(),
                ex.getErrorCode(),
                ex.getDetails()
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(response);
    }

    @ExceptionHandler(BaseCustomException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleBaseCustomException(
            BaseCustomException ex, WebRequest request) {

        log.error("Custom exception occurred: {}", ex.getMessage(), ex);

        ApiError apiError = new ApiError(
                ex.getHttpStatus().value(),
                ex.getMessage(),
                ex.getErrorCode(),
                ex.getDetails()
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, ex.getHttpStatus());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {

        log.error("Resource not found: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                ex.getErrorCode(),
                ex.getDetails()
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleOrderNotFoundException(
            OrderNotFoundException ex, WebRequest request) {

        log.error("Order not found: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        if (ex.getOrderId() != null) {
            details.put("orderId", ex.getOrderId().toString());
        }
        if (ex.getUserId() != null) {
            details.put("userId", ex.getUserId().toString());
        }

        ApiError apiError = new ApiError(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                "ORDER_NOT_FOUND",
                details
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleInvalidOrderStateException(
            InvalidOrderStateException ex, WebRequest request) {

        log.warn("Invalid order state transition: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        if (ex.getOrderId() != null) {
            details.put("orderId", ex.getOrderId().toString());
        }
        if (ex.getCurrentStatus() != null) {
            details.put("currentStatus", ex.getCurrentStatus().name());
        }
        if (ex.getTargetStatus() != null) {
            details.put("targetStatus", ex.getTargetStatus().name());
        }

        ApiError apiError = new ApiError(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                "INVALID_ORDER_STATE",
                details
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<ApiError>> handlePaymentNotFoundException(
            PaymentNotFoundException ex, WebRequest request) {

        log.error("Payment not found: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        if (ex.getPaymentId() != null) {
            details.put("paymentId", ex.getPaymentId().toString());
        }

        ApiError apiError = new ApiError(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                "PAYMENT_NOT_FOUND",
                details
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiResponse<ApiError>> handlePaymentFailedException(
            PaymentFailedException ex, WebRequest request) {

        log.error("Payment failed: {}", ex.getMessage());

        ApiError apiError = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                ex.getErrorCode()
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IdempotencyKeyViolationException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleIdempotencyKeyViolationException(
            IdempotencyKeyViolationException ex, WebRequest request) {

        log.warn("Idempotency key violation: {}", ex.getMessage());

        Map<String, Object> details = new HashMap<>();
        details.put("idempotencyKey", ex.getIdempotencyKey());

        ApiError apiError = new ApiError(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                "IDEMPOTENCY_KEY_VIOLATION",
                details
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        log.error("Validation exception: {}", ex.getMessage());

        Map<String, Object> validationErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.put(error.getField(), error.getDefaultMessage())
        );

        ApiError apiError = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                "VALIDATION_ERROR",
                validationErrors
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, "Validation failed");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ApiError>> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {

        log.error("Constraint violation: {}", ex.getMessage());

        Map<String, Object> validationErrors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                validationErrors.put(violation.getPropertyPath().toString(), violation.getMessage())
        );

        ApiError apiError = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                "Constraint violation",
                "CONSTRAINT_VIOLATION",
                validationErrors
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, "Constraint violation");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ApiError>> handleGenericException(
            Exception ex, WebRequest request) {

        log.error("Unexpected exception occurred: {}", ex.getMessage(), ex);

        ApiError apiError = new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error",
                "INTERNAL_ERROR"
        );
        apiError.setTraceId(MDC.get(TRACE_ID_KEY));

        ApiResponse<ApiError> response = ApiResponse.error(apiError, "Internal server error");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
