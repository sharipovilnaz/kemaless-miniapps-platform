package com.kemaless.core.exception.error;

import org.springframework.http.HttpStatus;

public class RealmRoleInUseException extends InUseException {
    public RealmRoleInUseException(String roleCode) {
        super("Realm role '%s' is used in access policies or mappings".formatted(roleCode));
    }
}
