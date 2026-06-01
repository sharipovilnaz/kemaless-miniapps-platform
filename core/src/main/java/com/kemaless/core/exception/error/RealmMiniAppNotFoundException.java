package com.kemaless.core.exception.error;

public class RealmMiniAppNotFoundException extends NotFoundException {
    public RealmMiniAppNotFoundException(String realmCode, String miniAppCode) {
        super("MiniApp '%s' is not connected to realm '%s'".formatted(miniAppCode, realmCode));
    }
}
