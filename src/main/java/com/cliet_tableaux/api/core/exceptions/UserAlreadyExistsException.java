package com.cliet_tableaux.api.core.exceptions;

import java.io.Serial;

public class UserAlreadyExistsException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -6096397759238448495L;

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
