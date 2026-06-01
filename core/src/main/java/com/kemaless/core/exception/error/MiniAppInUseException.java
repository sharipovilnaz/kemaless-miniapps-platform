package com.kemaless.core.exception.error;

public class MiniAppInUseException extends InUseException {
    public MiniAppInUseException(String code) {
        super("MiniApp '%s' is connected to one or more realms and cannot be deleted".formatted(code));
    }
}
