package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.ContactDto;
import com.cliet_tableaux.api.core.exceptions.GlobalExceptionHandler;
import com.cliet_tableaux.api.core.services.MailService;
import com.cliet_tableaux.api.core.services.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContactControllerTest {

    @InjectMocks
    private ContactController contactController;

    @Mock
    private MailService mailService;

    @Mock
    private RateLimiterService rateLimiterService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(contactController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void submitContactForm_withValidRequest_returns202() throws Exception {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), any())).thenReturn(true);
        ContactDto dto = new ContactDto("Jean Dupont", "jean.dupont@example.com", "Un message de contact");

        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isAccepted());

        verify(mailService, times(1)).envoyerMessageContact(any());
    }

    @Test
    void submitContactForm_withMissingName_returns400() throws Exception {
        ContactDto dto = new ContactDto("", "jean.dupont@example.com", "Un message de contact");

        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitContactForm_withInvalidEmail_returns400() throws Exception {
        ContactDto dto = new ContactDto("Jean Dupont", "pas-un-email", "Un message de contact");

        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitContactForm_withMissingMessage_returns400() throws Exception {
        ContactDto dto = new ContactDto("Jean Dupont", "jean.dupont@example.com", "");

        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitContactForm_whenRateLimited_returns429() throws Exception {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), any())).thenReturn(false);
        ContactDto dto = new ContactDto("Jean Dupont", "jean.dupont@example.com", "Un message de contact");

        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isTooManyRequests());
    }
}
