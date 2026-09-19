package com.cliet_tableaux.api.core.dtos;

public record UserResponseDto(Long id, String email, String firstName, String lastName, Boolean admin) {
}
