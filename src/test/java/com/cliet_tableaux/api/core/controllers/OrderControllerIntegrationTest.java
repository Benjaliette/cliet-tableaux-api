package com.cliet_tableaux.api.core.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.dtos.CheckoutSessionRequest;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.core.model.User;
import com.cliet_tableaux.api.core.services.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Vérifie bout en bout, via HTTP réel (MockMvc + contexte Spring complet), que les
// Phases 2 (authentification) et 4 (validation d'entrée, anti-survente) de l'audit
// ORDER_API_STATUS.md sont bien appliquées sur /api/v1/orders/create-checkout-session.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OrderControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PaintingDao paintingDao;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hashed-password");
        return userDao.save(user);
    }

    private Painting persistPainting(String title, boolean sold) {
        Painting painting = new Painting();
        painting.setTitle(title);
        painting.setDescription("Description");
        painting.setSell(sold);
        painting.setPriceCents(1000L);
        return paintingDao.save(painting);
    }

    // Phase 2 : sans JWT, la route est rejetée avant même d'atteindre le controller.
    @Test
    void createCheckoutSession_withoutToken_isRejected() throws Exception {
        Painting painting = persistPainting("Sans token", false);
        CheckoutSessionRequest request = new CheckoutSessionRequest(1000L, "1 rue de Paris", "EUR", painting.getId());

        mockMvc.perform(post("/api/v1/orders/create-checkout-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403);
                });
    }

    // Phase 4 : amount négatif rejeté par @Valid avant d'atteindre OrderService.
    @Test
    void createCheckoutSession_withInvalidBody_returns400() throws Exception {
        User user = persistUser("http-invalid@example.com");
        Painting painting = persistPainting("Body invalide", false);
        String token = jwtService.generateAccessToken(user);

        CheckoutSessionRequest invalidRequest = new CheckoutSessionRequest(-100L, "1 rue de Paris", "EUR", painting.getId());

        mockMvc.perform(post("/api/v1/orders/create-checkout-session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    // Phase 4 : Painting déjà vendu rejeté avec 409 Conflict.
    @Test
    void createCheckoutSession_withAlreadySoldPainting_returns409() throws Exception {
        User user = persistUser("http-sold@example.com");
        Painting painting = persistPainting("Déjà vendu", true);
        String token = jwtService.generateAccessToken(user);

        CheckoutSessionRequest request = new CheckoutSessionRequest(1000L, "1 rue de Paris", "EUR", painting.getId());

        mockMvc.perform(post("/api/v1/orders/create-checkout-session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // Chemin nominal complet : JWT valide + body valide + painting disponible → 201.
    @Test
    void createCheckoutSession_withValidRequest_returns201() throws Exception {
        User user = persistUser("http-valid@example.com");
        Painting painting = persistPainting("Disponible", false);
        String token = jwtService.generateAccessToken(user);

        CheckoutSessionRequest request = new CheckoutSessionRequest(1000L, "1 rue de Paris", "EUR", painting.getId());

        Session fakeSession = Mockito.mock(Session.class);
        Mockito.when(fakeSession.getId()).thenReturn("cs_test_http");
        Mockito.when(fakeSession.getUrl()).thenReturn("https://checkout.stripe.com/cs_test_http");

        try (MockedStatic<Session> mockedSession = Mockito.mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(fakeSession);

            mockMvc.perform(post("/api/v1/orders/create-checkout-session")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }
}
