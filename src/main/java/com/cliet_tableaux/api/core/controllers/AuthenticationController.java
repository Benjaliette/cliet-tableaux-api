package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.AuthDto;
import com.cliet_tableaux.api.core.dtos.AuthResponse;
import com.cliet_tableaux.api.core.dtos.UserDto;
import com.cliet_tableaux.api.core.services.AuthenticationService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationService authService;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    public AuthenticationController(AuthenticationService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody UserDto userDto, HttpServletResponse response) {
        AuthDto authDto = authService.login(userDto);
        setRefreshTokenCookie(response, authDto.refreshToken());
        return ResponseEntity.ok(new AuthResponse(authDto.user(), authDto.accessToken()));
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody UserDto userDto, HttpServletResponse response) {
        AuthDto authDto = authService.register(userDto);
        setRefreshTokenCookie(response, authDto.refreshToken());
        return new ResponseEntity<>(new AuthResponse(authDto.user(), authDto.accessToken()), HttpStatus.CREATED);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refresh_token") String refreshToken, HttpServletResponse response) {
        authService.logout(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@CookieValue(name = "refresh_token") String refreshToken, HttpServletResponse response) {
        AuthDto authDto = authService.refreshToken(refreshToken);
        setRefreshTokenCookie(response, authDto.refreshToken());
        return new ResponseEntity<>(new AuthResponse(authDto.user(), authDto.accessToken()), HttpStatus.OK);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(7 * 24 * 3600)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
