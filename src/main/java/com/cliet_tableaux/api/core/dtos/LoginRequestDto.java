package com.cliet_tableaux.api.core.dtos;

// DTO d'entrée dédié au login. Distinct de SignupRequestDto (qui porte firstName/lastName,
// non pertinents ici) et de UserResponseDto (jamais de password en sortie).
public record LoginRequestDto(String email, String password) {
}
