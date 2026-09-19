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

    @Test
    void createCheckoutSession_withRefreshTokenInAuthHeader_isRejected401() throws Exception {
        User user = persistUser("http-refresh-as-access@example.com");
        Painting painting = persistPainting("Refresh token en Authorization", false);
        String refreshToken = jwtService.generateRefreshToken(user);

        CheckoutSessionRequest request = new CheckoutSessionRequest(1000L, "1 rue de Paris", "EUR", painting.getId());

        mockMvc.perform(post("/api/v1/orders/create-checkout-session")
                        .header("Authorization", "Bearer " + refreshToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(401));
    }

    @Test
    void createCheckoutSession_withAccessTokenInAuthHeader_isAllowed() throws Exception {
        User user = persistUser("http-access-token-ok@example.com");
        Painting painting = persistPainting("Access token normal", false);
        String accessToken = jwtService.generateAccessToken(user);

        CheckoutSessionRequest request = new CheckoutSessionRequest(1000L, "1 rue de Paris", "EUR", painting.getId());

        Session fakeSession = Mockito.mock(Session.class);
        Mockito.when(fakeSession.getId()).thenReturn("cs_test_access_ok");
        Mockito.when(fakeSession.getUrl()).thenReturn("https://checkout.stripe.com/cs_test_access_ok");

        try (MockedStatic<Session> mockedSession = Mockito.mockStatic(Session.class)) {
            mockedSession.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(fakeSession);

            mockMvc.perform(post("/api/v1/orders/create-checkout-session")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

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
