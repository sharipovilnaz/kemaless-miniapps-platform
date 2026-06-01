package com.kemaless.core.exception.error;

import java.util.UUID;

public class PlatformUserNotFoundException extends NotFoundException{
    public PlatformUserNotFoundException(UUID id) {
        super("Platform user with id %s not found".formatted(id));
    }
}
