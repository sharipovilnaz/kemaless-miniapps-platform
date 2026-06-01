package com.kemaless.miniappsdk.exception;

import org.springframework.http.HttpStatus;

public class MiniappAuthException extends BaseException{
    public MiniappAuthException(String message, HttpStatus status) {
        super(message, status);
    }

    public MiniappAuthException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
