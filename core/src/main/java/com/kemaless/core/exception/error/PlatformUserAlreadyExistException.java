package com.kemaless.core.exception.error;

import java.util.UUID;

public class PlatformUserAlreadyExistException extends AlreadyExistException{
    public PlatformUserAlreadyExistException(String name) {
        super("Platform user with name %s already exist exception".formatted(name));
    }
}
