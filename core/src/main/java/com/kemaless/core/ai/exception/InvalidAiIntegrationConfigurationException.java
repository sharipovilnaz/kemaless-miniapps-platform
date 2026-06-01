package com.kemaless.core.ai.exception;

import com.kemaless.core.auth.exception.ServiceException;

public class InvalidAiIntegrationConfigurationException extends ServiceException {

    public InvalidAiIntegrationConfigurationException(String message) {
        super(message, 400, "InvalidAiIntegrationConfiguration");
    }
}
