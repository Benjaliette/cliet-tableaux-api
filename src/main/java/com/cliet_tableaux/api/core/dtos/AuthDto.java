package com.cliet_tableaux.api.core.dtos;

public record AuthDto(UserResponseDto user, String accessToken, String refreshToken) {
}
