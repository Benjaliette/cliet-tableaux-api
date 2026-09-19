package com.cliet_tableaux.api.core.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CustomRequestDto(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotBlank String description,
        String budget
) {
}
