package com.kemaless.core.auth.exception;

public class MiniappAccessDeniedException extends ServiceException {
    public MiniappAccessDeniedException(String name) {
        super(name, 403);
    }
}
