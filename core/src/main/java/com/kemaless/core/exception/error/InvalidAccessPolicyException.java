package com.kemaless.core.exception.error;

import com.kemaless.core.auth.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class InvalidAccessPolicyException extends ServiceException {
    public InvalidAccessPolicyException(String message) {
        super(message, HttpStatus.CONFLICT.value());
    }
}
