package com.kemaless.core.exception.error;

import com.kemaless.core.auth.exception.ServiceException;

public class AlreadyExistException extends ServiceException {
    public AlreadyExistException(String message) {
        super(message, 409, "AlreadyExists");
    }
}
