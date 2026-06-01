package com.kemaless.core.exception.error;

public class MiniAppAlreadyExistException extends AlreadyExistException {
    public MiniAppAlreadyExistException(String code) {
        super("MiniApp with code '%s' already exists in the global catalog".formatted(code));
    }
}
