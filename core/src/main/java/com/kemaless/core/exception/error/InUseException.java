package com.kemaless.core.exception.error;

import com.kemaless.core.auth.exception.ServiceException;
import org.springframework.http.HttpStatus;

public class InUseException extends ServiceException {
    public InUseException(String message) {
        super(message, HttpStatus.CONFLICT.value(), null);
    }
}
