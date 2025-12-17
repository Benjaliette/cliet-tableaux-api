package com.cliet_tableaux.api.core.dtos;

public record AuthDto(UserDto user, String accessToken, String refreshToken) {
}
