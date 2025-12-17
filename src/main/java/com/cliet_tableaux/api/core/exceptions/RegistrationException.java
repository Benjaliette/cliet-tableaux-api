package com.cliet_tableaux.api.core.exceptions;

import java.io.Serial;

public class RegistrationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 5902376811262735489L;

    public RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
