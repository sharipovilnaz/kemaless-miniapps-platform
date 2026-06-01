package com.kemaless.core.auth.exception;

import org.springframework.http.HttpStatus;

public class AuthException extends ServiceException {

    public AuthException(String message) {
        super(message, HttpStatus.UNAUTHORIZED.value());
    }
}
