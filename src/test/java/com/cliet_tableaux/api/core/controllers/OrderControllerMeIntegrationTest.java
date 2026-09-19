package com.cliet_tableaux.api.core.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cliet_tableaux.api.core.daos.OrderDao;
import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.model.Order;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.core.model.User;
import com.cliet_tableaux.api.core.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OrderControllerMeIntegrationTest {

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
    private OrderDao orderDao;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hashed-password");
        return userDao.save(user);
    }

    private Painting persistPainting(String title) {
        Painting painting = new Painting();
        painting.setTitle(title);
        painting.setDescription("Description");
        painting.setSell(false);
        painting.setPriceCents(1000L);
        painting.setImagePublicId("paintings/" + title);
        return paintingDao.save(painting);
    }

    private Order persistOrder(User user, Painting painting, Long amountCents) {
        Order order = Order.create(amountCents, "1 rue de Paris", "EUR", user, painting);
        return orderDao.save(order);
    }

    @Test
    void getMyOrders_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyOrders_withNoOrders_returnsEmptyList() throws Exception {
        User user = persistUser("no-orders@example.com");
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[]"));
    }

    @Test
    void getMyOrders_returnsOnlyOwnOrders_sortedByMostRecentFirst() throws Exception {
        User self = persistUser("orders-self@example.com");
        User other = persistUser("orders-other@example.com");

        Painting painting1 = persistPainting("Premier tableau commandé");
        Painting painting2 = persistPainting("Second tableau commandé");
        Painting otherPainting = persistPainting("Tableau d'un autre utilisateur");

        Order order1 = persistOrder(self, painting1, 1000L);
        // createdAt est posé automatiquement (auditing JPA) avec une précision milliseconde :
        // on espace les créations pour garantir un ordre déterministe dans l'assertion de tri.
        Thread.sleep(10);
        Order order2 = persistOrder(self, painting2, 2000L);
        Order otherOrder = persistOrder(other, otherPainting, 5000L);

        String token = jwtService.generateAccessToken(self);

        MvcResult result = mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body).hasSize(2);

        // Commande la plus récente en premier
        assertThat(body.get(0).get("id").asLong()).isEqualTo(order2.getId());
        assertThat(body.get(1).get("id").asLong()).isEqualTo(order1.getId());

        // Aucune commande de l'autre utilisateur ne doit apparaître
        for (JsonNode orderNode : body) {
            assertThat(orderNode.get("id").asLong()).isNotEqualTo(otherOrder.getId());
        }

        JsonNode firstOrder = body.get(0);
        assertThat(firstOrder.get("status").asText()).isEqualTo("PENDING");
        assertThat(firstOrder.get("amountCents").asLong()).isEqualTo(2000L);
        assertThat(firstOrder.get("currency").asText()).isEqualTo("EUR");
        assertThat(firstOrder.get("paintingId").asLong()).isEqualTo(painting2.getId());
        assertThat(firstOrder.get("paintingTitle").asText()).isEqualTo("Second tableau commandé");
        assertThat(firstOrder.get("paintingImagePublicId").asText()).isEqualTo(painting2.getImagePublicId());
    }

    @Test
    void getMyOrders_cannotBeInfluencedByAnyClientSuppliedUserId() throws Exception {
        User self = persistUser("orders-idor-self@example.com");
        User other = persistUser("orders-idor-other@example.com");

        Painting painting = persistPainting("Tableau IDOR");
        persistOrder(other, painting, 3000L);

        String token = jwtService.generateAccessToken(self);

        // Aucun paramètre userId/id n'existe sur cette route : on vérifie qu'ajouter
        // un query param arbitraire ne permet pas de récupérer les commandes d'autrui.
        mockMvc.perform(get("/api/v1/orders/me")
                        .header("Authorization", "Bearer " + token)
                        .param("userId", other.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[]"));
    }
}
