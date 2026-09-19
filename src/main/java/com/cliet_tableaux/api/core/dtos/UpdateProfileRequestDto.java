package com.cliet_tableaux.api.core.dtos;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequestDto(
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
