package com.kemaless.core.exception.error;

public class RealmAlreadyExistException extends AlreadyExistException{
    public RealmAlreadyExistException(String code) {
        super("Realm with code %s already exist".formatted(code));
    }
}
