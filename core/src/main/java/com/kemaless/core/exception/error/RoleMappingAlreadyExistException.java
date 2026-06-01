package com.kemaless.core.exception.error;

public class RoleMappingAlreadyExistException extends AlreadyExistException {
    public RoleMappingAlreadyExistException(String realmCode, String externalRole) {
        super("Role mapping for external role '%s' already exists in realm '%s'".formatted(externalRole, realmCode));
    }
}
