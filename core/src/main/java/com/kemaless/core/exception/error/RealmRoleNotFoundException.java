package com.kemaless.core.exception.error;

public class RealmRoleNotFoundException extends NotFoundException {
    public RealmRoleNotFoundException(String realmCode, String roleCode) {
        super("Realm role with code '%s' not found in realm '%s'".formatted(roleCode, realmCode));
    }
}
