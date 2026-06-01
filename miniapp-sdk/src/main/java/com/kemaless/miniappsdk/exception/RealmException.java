package com.kemaless.miniappsdk.exception;

import org.springframework.http.HttpStatus;

public class RealmException extends BaseException{
    public RealmException(String message, HttpStatus status) {
        super(message, status);
    }

    public RealmException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
