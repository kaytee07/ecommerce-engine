package com.shop.ecommerceengine.identity.exception;

import com.shop.ecommerceengine.common.exception.BaseCustomException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to create a user with existing username or email.
 */
public class UserExistsException extends BaseCustomException {

    public static final String USERNAME_EXISTS = "USERNAME_EXISTS";
    public static final String EMAIL_EXISTS = "EMAIL_EXISTS";

    public UserExistsException(String message, String errorCode) {
        super(message, HttpStatus.CONFLICT, errorCode);
    }

    public static UserExistsException usernameExists(String username) {
        return new UserExistsException("Username '" + username + "' is already taken", USERNAME_EXISTS);
    }

    public static UserExistsException emailExists(String email) {
        return new UserExistsException("Email '" + email + "' is already registered", EMAIL_EXISTS);
    }
}
