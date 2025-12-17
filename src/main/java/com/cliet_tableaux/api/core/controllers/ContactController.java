package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.ContactDto;
import com.cliet_tableaux.api.core.services.EmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

    private final EmailService emailService;

    public ContactController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping
    public ResponseEntity<Void> submitContactForm(@Valid @RequestBody ContactDto contactDto) {
        emailService.sendContactFormEmail(contactDto);

        return ResponseEntity.accepted().build();
    }
}
