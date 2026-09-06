package com.cliet_tableaux.api.core.exceptions;

import com.cliet_tableaux.api.core.dtos.ErrorResponse;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        logger.debug("Authentication error handled: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(e.getMessage(), HttpStatus.UNAUTHORIZED.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException e) {
        logger.debug("User already exists error handled: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(e.getMessage(), HttpStatus.CONFLICT.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ErrorResponse> handleRegistration(RegistrationException e) {
        logger.debug("Registration error handled: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse("Registration failed", HttpStatus.BAD_REQUEST.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        logger.debug("Validation error handled: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // Sans ce handler, un échec de @Valid sur un @RequestBody (MethodArgumentNotValidException)
    // tombe dans handleGeneral ci-dessous et renvoie 500 au lieu de 400 : ce ControllerAdvice
    // définit son propre handler générique Exception.class, qui prime sur le traitement par
    // défaut de Spring MVC pour les erreurs de validation.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        logger.debug("Validation error handled: {}", message);

        ErrorResponse error = new ErrorResponse(message, HttpStatus.BAD_REQUEST.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(PaintingAlreadySoldException.class)
    public ResponseEntity<ErrorResponse> handlePaintingAlreadySold(PaintingAlreadySoldException e) {
        logger.debug("Painting already sold error handled: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(e.getMessage(), HttpStatus.CONFLICT.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // Sans ce handler, ResourceNotFoundException (Painting/User introuvable, notamment sur les
    // routes de commande) est bien traduite en 404 grâce à son @ResponseStatus, mais avec le body
    // d'erreur par défaut de Spring Boot (timestamp/status/error/path) plutôt que le format
    // ErrorResponse utilisé partout ailleurs dans l'API.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e) {
        logger.debug("Resource not found error handled: {}", e.getMessage());

        ErrorResponse error = new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        logger.error("Unexpected error occurred", e);

        ErrorResponse error = new ErrorResponse("An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR.value(), LocalDateTime.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(value = {ExpiredJwtException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ResponseEntity<Object> handleExpiredJwtException(ExpiredJwtException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "JWT Token has expired");
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }
}

