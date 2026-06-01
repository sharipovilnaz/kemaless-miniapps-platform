package com.kemaless.core.exception.error;

public class RealmNotFoundException extends NotFoundException{
    public RealmNotFoundException(String code) {
        super("Realm with code %s not found".formatted(code));
    }
}
