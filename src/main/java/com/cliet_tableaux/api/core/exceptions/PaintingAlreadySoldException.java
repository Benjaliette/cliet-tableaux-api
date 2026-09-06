package com.cliet_tableaux.api.core.exceptions;

import java.io.Serial;

public class PaintingAlreadySoldException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PaintingAlreadySoldException(String message) {
        super(message);
    }
}
