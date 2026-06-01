package com.kemaless.core.exception.error;

import com.kemaless.core.auth.exception.ServiceException;

public class NotFoundException extends ServiceException {
    public NotFoundException(String message) {
        super(message, 404, "NotFound");
    }
}
