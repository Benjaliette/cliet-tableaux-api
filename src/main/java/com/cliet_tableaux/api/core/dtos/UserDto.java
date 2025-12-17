package com.cliet_tableaux.api.core.dtos;

public record UserDto(Long id, String email, String password, String firstName, String lastName, Boolean admin) {
}
