package com.kemaless.core.ai.exception;

import com.kemaless.core.auth.exception.ServiceException;


public class MediaStorageUnavailableException extends ServiceException {

    public MediaStorageUnavailableException(String message) {
        super(message, 503, "MediaStorageUnavailable");
    }
}
