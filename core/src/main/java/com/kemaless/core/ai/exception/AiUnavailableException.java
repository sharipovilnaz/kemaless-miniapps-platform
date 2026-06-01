package com.kemaless.core.ai.exception;

import com.kemaless.core.auth.exception.ServiceException;

public class AiUnavailableException extends ServiceException {

    public AiUnavailableException(String message) {
        super(message, 503, "AiUnavailable");
    }
}
