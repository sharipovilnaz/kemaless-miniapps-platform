package com.kemaless.core.exception.error;

public class MiniAppNotFoundException extends NotFoundException {
    public MiniAppNotFoundException(String code) {
        super("MiniApp with code '%s' not found".formatted(code));
    }
}