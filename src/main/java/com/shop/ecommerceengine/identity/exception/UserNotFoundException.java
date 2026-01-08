package com.shop.ecommerceengine.identity.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when a requested user is not found.
 */
public class UserNotFoundException extends BaseCustomException {

    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";

    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, USER_NOT_FOUND);
    }

    public static UserNotFoundException byId(UUID id) {
        return new UserNotFoundException("User not found with id: " + id);
    }

    public static UserNotFoundException byUsername(String username) {
        return new UserNotFoundException("User not found with username: " + username);
    }

    public static UserNotFoundException byEmail(String email) {
        return new UserNotFoundException("User not found with email: " + email);
    }
}
