package com.cliet_tableaux.api.core.dtos;

// DTO de sortie unique pour toute réponse exposant un User (signup, login, refresh,
// GET /api/v1/users) : ne porte jamais password. Voir AUDIT_BACKEND.md, finding #5.
public record UserResponseDto(Long id, String email, String firstName, String lastName, Boolean admin) {
}
