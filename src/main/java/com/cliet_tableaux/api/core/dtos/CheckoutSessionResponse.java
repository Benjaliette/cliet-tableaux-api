package com.cliet_tableaux.api.core.dtos;

public record CheckoutSessionResponse(String sessionId, String url, Long paymentId) {
}
