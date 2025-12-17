package com.cliet_tableaux.api.core.dtos;

import java.time.LocalDateTime;

public record ErrorResponse(String message, int status, LocalDateTime timestamp) {
}