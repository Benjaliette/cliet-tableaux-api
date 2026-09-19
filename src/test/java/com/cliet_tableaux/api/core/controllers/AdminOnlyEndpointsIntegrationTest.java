package com.cliet_tableaux.api.core.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.dtos.CloudinarySignatureRequestDto;
import com.cliet_tableaux.api.core.dtos.DimensionDto;
import com.cliet_tableaux.api.core.dtos.PaintingDto;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.core.model.User;
import com.cliet_tableaux.api.core.services.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Vérifie AUDIT_BACKEND.md, findings #4 (endpoints publics à restreindre) et #9 (préfixe
// ROLE_ requis pour que hasRole("ADMIN") fonctionne réellement) : écriture du catalogue
// (POST/PUT/DELETE /paintings), GET /users et POST /cloudinary-signature sont désormais
// réservés à ROLE_ADMIN.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AdminOnlyEndpointsIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User persistUser(String email, boolean admin) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("mot-de-passe"));
        user.setAdmin(admin);
        return userDao.save(user);
    }

    private Painting persistPainting(String title) {
        Painting painting = new Painting();
        painting.setTitle(title);
        painting.setDescription("Description");
        painting.setSell(false);
        painting.setPriceCents(1000L);
        return paintingDao.save(painting);
    }

    // Les 5 requêtes désormais réservées à ROLE_ADMIN. Reconstruites à chaque appel : le body
    // JSON d'un MockHttpServletRequestBuilder ne peut être consommé qu'une fois exécuté.
    private List<MockHttpServletRequestBuilder> adminOnlyRequests(Long existingPaintingId) throws Exception {
        PaintingDto paintingToCreate = new PaintingDto(null, "Nouveau tableau", "Description", "Huile sur toile",
                new DimensionDto(50, 70), false, "1000", "EUR", null, null);
        PaintingDto paintingToUpdate = new PaintingDto(existingPaintingId, "Titre modifié", "Description",
                "Huile sur toile", new DimensionDto(50, 70), false, "1000", "EUR", null, null);

        return List.of(
                post("/api/v1/paintings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paintingToCreate)),
                put("/api/v1/paintings/" + existingPaintingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paintingToUpdate)),
                delete("/api/v1/paintings/" + existingPaintingId),
                get("/api/v1/users"),
                post("/api/v1/cloudinary-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CloudinarySignatureRequestDto(null)))
        );
    }

    @Test
    void adminOnlyEndpoints_withoutToken_return401() throws Exception {
        Painting painting = persistPainting("Sans token");

        for (MockHttpServletRequestBuilder request : adminOnlyRequests(painting.getId())) {
            mockMvc.perform(request)
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(401));
        }
    }

    @Test
    void adminOnlyEndpoints_withNonAdminToken_return403() throws Exception {
        User nonAdmin = persistUser("simple-user@example.com", false);
        String token = jwtService.generateAccessToken(nonAdmin);
        Painting painting = persistPainting("Utilisateur non-admin");

        for (MockHttpServletRequestBuilder request : adminOnlyRequests(painting.getId())) {
            mockMvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(403));
        }
    }

    @Test
    void adminOnlyEndpoints_withAdminToken_areAllowed() throws Exception {
        User admin = persistUser("admin-user@example.com", true);
        String token = jwtService.generateAccessToken(admin);
        Painting painting = persistPainting("Utilisateur admin");

        // Exécutées dans l'ordre : POST (crée un autre tableau), PUT (modifie "painting"),
        // DELETE (supprime ce même "painting", en dernier des requêtes touchant ce tableau).
        for (MockHttpServletRequestBuilder request : adminOnlyRequests(painting.getId())) {
            mockMvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
        }
    }

    // Sanity check : finding #4 ne restreint que l'écriture, la consultation du catalogue doit
    // rester publique, avec ou sans authentification.
    @Test
    void paintingCatalog_readEndpoints_remainPublic() throws Exception {
        Painting painting = persistPainting("Consultable par tous");

        mockMvc.perform(get("/api/v1/paintings"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        mockMvc.perform(get("/api/v1/paintings/" + painting.getId()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }
}
