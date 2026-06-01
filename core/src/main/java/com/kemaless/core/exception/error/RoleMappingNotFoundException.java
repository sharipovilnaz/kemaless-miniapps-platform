package com.kemaless.core.exception.error;

import java.util.UUID;

public class RoleMappingNotFoundException extends NotFoundException {
    public RoleMappingNotFoundException(UUID id) {
        super("Role mapping with id '%s' not found".formatted(id));
    }
}
