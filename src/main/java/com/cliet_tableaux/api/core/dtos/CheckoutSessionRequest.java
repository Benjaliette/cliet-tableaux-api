package com.cliet_tableaux.api.core.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CheckoutSessionRequest(
        @NotNull @Positive Long amount,
        @NotBlank String address,
        @Pattern(regexp = "^$|^[A-Za-z]{3}$", message = "currency doit être vide ou un code ISO 4217 à 3 lettres (ex: EUR)")
        String currency,
        @NotNull Long paintingId
) {
}
