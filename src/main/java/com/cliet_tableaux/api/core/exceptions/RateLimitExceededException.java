package com.cliet_tableaux.api.core.exceptions;

import java.io.Serial;

public class RateLimitExceededException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public RateLimitExceededException(String message) {
        super(message);
    }
}
