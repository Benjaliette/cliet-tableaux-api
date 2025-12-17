package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.dtos.AuthDto;
import com.cliet_tableaux.api.core.dtos.UserDto;
import com.cliet_tableaux.api.core.exceptions.AuthenticationException;
import com.cliet_tableaux.api.core.exceptions.ExpiredTokenException;
import com.cliet_tableaux.api.core.exceptions.ResourceNotFoundException;
import com.cliet_tableaux.api.core.exceptions.UserAlreadyExistsException;
import com.cliet_tableaux.api.core.mappers.UserMapper;
import com.cliet_tableaux.api.core.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;

    public AuthenticationService(UserService userService,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService,
                                 AuthenticationManager authenticationManager,
                                 UserMapper userMapper) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.userMapper = userMapper;
    }

    // LOGIN
    public AuthDto login(UserDto request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            User user = (User) userService.loadUserByUsername(request.email());

            if (passwordEncoder.matches(request.password(), user.getPassword())) {
                return buildAuthDto(user);
            } else {
                throw new AuthenticationException("");
            }
        } catch (Exception e) {
            logger.error("Unexpected error during login for user: {}", request.email(), e);
            throw new AuthenticationException("Login failed", e);
        }
    }

    // REGISTER
    public AuthDto register(UserDto request) {
        try {
            if (userService.existsByEmail(request.email())) {
                throw new UserAlreadyExistsException("Email already exists");
            }

            User newUser = userMapper.toEntity(request);
            String encryptedPassword = passwordEncoder.encode(request.password());
            newUser.setPassword(encryptedPassword);

            User savedUser = userService.save(newUser);

            return buildAuthDto(savedUser);
        } catch (Exception e) {
            logger.error("Unexpected error during login for user: {}", request.email(), e);
            throw new AuthenticationException("Login failed", e);
        }
    }

    public void logout(String refreshToken) {
        try {
            User user = userService.findUserByRefreshToken(refreshToken);

            user.setRefreshToken(null);
            userService.save(user);
        } catch (ResourceNotFoundException e) {
            logger.error("No user found with refresh token {}", refreshToken, e);
        }

        SecurityContextHolder.clearContext();
    }

    public AuthDto refreshToken(String refreshToken) {
        User user = userService.findUserByRefreshToken(refreshToken);

        if (jwtService.isTokenExpired(refreshToken)) {
            user.setRefreshToken(null);
            userService.save(user);
            throw new ExpiredTokenException("Refresh token has expired. Please log in again.");
        }

        return buildAuthDto(user);
    }

    private AuthDto buildAuthDto(final User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        user.setRefreshToken(refreshToken);
        userService.save(user);

        return new AuthDto(userMapper.toDto(user), accessToken, refreshToken);
    }
}
