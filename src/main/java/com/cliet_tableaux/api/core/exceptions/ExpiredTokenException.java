package com.cliet_tableaux.api.core.exceptions;

import java.io.Serial;

public class ExpiredTokenException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 3910910304933383757L;

    public ExpiredTokenException(String message) {
        super(message);
    }

    public ExpiredTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
