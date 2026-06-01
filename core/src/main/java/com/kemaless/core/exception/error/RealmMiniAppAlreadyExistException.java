package com.kemaless.core.exception.error;

public class RealmMiniAppAlreadyExistException extends AlreadyExistException{
    public RealmMiniAppAlreadyExistException(String realmCode, String miniAppCode) {
        super("MiniApp '%s' is already connected to realm '%s'".formatted(miniAppCode, realmCode));
    }
}
