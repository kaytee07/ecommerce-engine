package com.shop.ecommerceengine.catalog.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * Exception thrown when a discount operation is invalid.
 * Examples: discount > 100%, negative discount, invalid date range.
 */
public class InvalidDiscountException extends BaseCustomException {

    private static final String ERROR_CODE = "INVALID_DISCOUNT";

    public InvalidDiscountException(String message) {
        super(message, HttpStatus.BAD_REQUEST, ERROR_CODE);
    }

    public static InvalidDiscountException percentageOutOfRange(BigDecimal percentage) {
        InvalidDiscountException ex = new InvalidDiscountException(
                String.format("Discount percentage must be between 0 and 100, got: %s", percentage)
        );
        ex.addDetail("percentage", percentage);
        return ex;
    }

    public static InvalidDiscountException invalidDateRange() {
        return new InvalidDiscountException("Discount end date must be after start date");
    }

    public static InvalidDiscountException noTargetSpecified() {
        return new InvalidDiscountException("Either categoryId or applyToAll must be specified for bulk discount");
    }
}
