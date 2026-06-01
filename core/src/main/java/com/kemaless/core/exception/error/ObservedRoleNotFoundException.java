package com.kemaless.core.exception.error;

import java.util.UUID;

public class ObservedRoleNotFoundException extends NotFoundException {
    public ObservedRoleNotFoundException(UUID id) {
        super("Observed role with id '%s' not found".formatted(id));
    }
}
