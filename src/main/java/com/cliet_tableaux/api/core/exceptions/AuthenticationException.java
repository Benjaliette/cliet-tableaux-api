package com.cliet_tableaux.api.core.exceptions;

import java.io.Serial;

public class AuthenticationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 8189556205748502749L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
