package com.cliet_tableaux.api.core.dtos;

public record AuthResponse(UserResponseDto user, String accessToken) {
}
