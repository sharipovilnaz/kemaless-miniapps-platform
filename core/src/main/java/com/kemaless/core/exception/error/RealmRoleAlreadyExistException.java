package com.kemaless.core.exception.error;

public class RealmRoleAlreadyExistException extends AlreadyExistException {
    public RealmRoleAlreadyExistException(String realmCode, String roleCode) {
        super("Realm role with code '%s' already exists in realm '%s'".formatted(roleCode, realmCode));
    }
}
