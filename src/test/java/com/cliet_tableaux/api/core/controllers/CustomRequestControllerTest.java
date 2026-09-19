package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.exceptions.GlobalExceptionHandler;
import com.cliet_tableaux.api.core.services.MailService;
import com.cliet_tableaux.api.core.services.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CustomRequestControllerTest {

    // Signature JPEG minimale valide (magic bytes FF D8 FF), suffisante pour ReferenceImageValidator.
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};

    @InjectMocks
    private CustomRequestController customRequestController;

    @Mock
    private MailService mailService;

    @Mock
    private RateLimiterService rateLimiterService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(customRequestController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void submitCustomRequest_withValidRequestAndImage_returns201() throws Exception {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), any())).thenReturn(true);
        MockMultipartFile image = new MockMultipartFile("referenceImage", "photo.jpg", "image/jpeg", JPEG_BYTES);

        mockMvc.perform(multipart("/api/v1/custom-requests")
                        .file(image)
                        .param("name", "Jean Dupont")
                        .param("email", "jean.dupont@example.com")
                        .param("description", "Un portrait de mon chat façon impressionniste"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());

        verify(mailService, times(1)).envoyerDemandeSurMesure(any(), any());
    }

    @Test
    void submitCustomRequest_withoutImage_returns201() throws Exception {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), any())).thenReturn(true);

        mockMvc.perform(multipart("/api/v1/custom-requests")
                        .param("name", "Jean Dupont")
                        .param("email", "jean.dupont@example.com")
                        .param("description", "Un portrait de mon chat façon impressionniste"))
                .andExpect(status().isCreated());
    }

    @Test
    void submitCustomRequest_withMissingDescription_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/custom-requests")
                        .param("name", "Jean Dupont")
                        .param("email", "jean.dupont@example.com"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitCustomRequest_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/custom-requests")
                        .param("name", "Jean Dupont")
                        .param("email", "pas-un-email")
                        .param("description", "Un portrait de mon chat"))
                .andExpect(status().isBadRequest());
    }

    // Le rejet des fichiers dont le type MIME réel n'est pas une image supportée est testé
    // directement sur ReferenceImageValidator (voir ReferenceImageValidatorTest) : ici,
    // MailService est mocké, donc la validation réelle du fichier ne s'exécute pas.

    @Test
    void submitCustomRequest_whenRateLimited_returns429() throws Exception {
        when(rateLimiterService.isAllowed(anyString(), anyInt(), any())).thenReturn(false);

        mockMvc.perform(multipart("/api/v1/custom-requests")
                        .param("name", "Jean Dupont")
                        .param("email", "jean.dupont@example.com")
                        .param("description", "Un portrait de mon chat"))
                .andExpect(status().isTooManyRequests());
    }
}
