package com.kemaless.miniappsdk.exception;

import org.springframework.http.HttpStatus;

public class BaseException extends RuntimeException {
    HttpStatus status;

    public BaseException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

}
