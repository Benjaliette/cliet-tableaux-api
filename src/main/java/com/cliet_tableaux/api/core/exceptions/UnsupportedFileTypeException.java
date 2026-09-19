package com.cliet_tableaux.api.core.exceptions;

import java.io.Serial;

public class UnsupportedFileTypeException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
