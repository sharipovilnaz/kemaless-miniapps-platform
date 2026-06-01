package com.kemaless.core.auth.exception;

public class ServiceException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public ServiceException(String message, int status) {
        this(message, status, null);
    }

    public ServiceException(String message, int status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode != null ? errorCode : getClass().getSimpleName();
    }
}