package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.CustomRequestDto;
import com.cliet_tableaux.api.core.dtos.MessageResponse;
import com.cliet_tableaux.api.core.exceptions.RateLimitExceededException;
import com.cliet_tableaux.api.core.services.MailService;
import com.cliet_tableaux.api.core.services.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/custom-requests")
public class CustomRequestController {

    // Anti-spam basique : au-delà de ce nombre de demandes pour la même IP sur la fenêtre
    // ci-dessous, les requêtes suivantes sont rejetées (429). Voir RateLimiterService pour les
    // limites de cette approche (en mémoire, par instance).
    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final MailService mailService;
    private final RateLimiterService rateLimiterService;

    public CustomRequestController(MailService mailService, RateLimiterService rateLimiterService) {
        this.mailService = mailService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> submitCustomRequest(
            @Valid @ModelAttribute CustomRequestDto customRequestDto,
            @RequestParam(value = "referenceImage", required = false) MultipartFile referenceImage,
            HttpServletRequest request) {

        String clientIp = request.getRemoteAddr();
        if (!rateLimiterService.isAllowed("ip:" + clientIp, MAX_REQUESTS, WINDOW)
                || !rateLimiterService.isAllowed("email:" + customRequestDto.email(), MAX_REQUESTS, WINDOW)) {
            throw new RateLimitExceededException("Trop de demandes envoyées récemment, merci de réessayer plus tard");
        }

        mailService.envoyerDemandeSurMesure(customRequestDto, referenceImage);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("Votre demande a bien été envoyée, nous vous répondrons rapidement."));
    }
}
