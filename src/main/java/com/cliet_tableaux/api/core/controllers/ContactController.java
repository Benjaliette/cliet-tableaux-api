package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.ContactDto;
import com.cliet_tableaux.api.core.exceptions.RateLimitExceededException;
import com.cliet_tableaux.api.core.services.MailService;
import com.cliet_tableaux.api.core.services.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

  private final MailService emailService;
  private final RateLimiterService rateLimiterService;

  public ContactController(MailService emailService, RateLimiterService rateLimiterService) {
    this.emailService = emailService;
    this.rateLimiterService = rateLimiterService;
  }

  @PostMapping
  public ResponseEntity<Void> submitContactForm(@Valid @RequestBody ContactDto contactDto, HttpServletRequest request) {
    String clientIp = request.getRemoteAddr();
    if (!rateLimiterService.isAllowed("ip:" + clientIp, RateLimiterService.DEFAULT_MAX_REQUESTS, RateLimiterService.DEFAULT_WINDOW)
            || !rateLimiterService.isAllowed("email:" + contactDto.email(), RateLimiterService.DEFAULT_MAX_REQUESTS, RateLimiterService.DEFAULT_WINDOW)) {
      throw new RateLimitExceededException("Trop de demandes envoyées récemment, merci de réessayer plus tard");
    }

    emailService.envoyerMessageContact(contactDto);

    return ResponseEntity.accepted().build();
  }
}
