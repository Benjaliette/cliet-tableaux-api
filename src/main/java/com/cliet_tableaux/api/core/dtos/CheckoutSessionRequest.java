package com.cliet_tableaux.api.core.dtos;

public record CheckoutSessionRequest(Long amount, String address, String currency, Long userId, Long paintingId) {
}
