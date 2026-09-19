package com.cliet_tableaux.api.core.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.model.User;
import com.cliet_tableaux.api.core.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Vérifie que la refonte UserDto -> SignupRequestDto / LoginRequestDto / UserResponseDto
// (AUDIT_BACKEND.md, findings #1, #2, #5) élimine bien à la racine :
// - le mass assignment de l'id client à l'inscription (écrasement d'un compte existant),
// - le mass assignment du champ admin à l'inscription (auto-élévation de privilèges),
// - la fuite du hash de mot de passe dans les réponses JSON exposant un utilisateur.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthenticationControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User persistUser(String email, String rawPassword, boolean admin) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setAdmin(admin);
        user.setFirstName("Existing");
        user.setLastName("User");
        return userDao.save(user);
    }

    // Finding #1 : un attaquant qui connaît (ou devine, les id étant séquentiels) l'id d'un
    // compte existant et envoie ce même id dans le payload de signup ne doit jamais pouvoir
    // écraser ce compte (ancien comportement : merge() JPA sur id non-null).
    @Test
    void signup_withIdOfExistingUser_doesNotOverwriteExistingUser() throws Exception {
        User existingUser = persistUser("victime@example.com", "mot-de-passe-original", false);
        String originalEncryptedPassword = existingUser.getPassword();

        Map<String, Object> maliciousPayload = new LinkedHashMap<>();
        maliciousPayload.put("id", existingUser.getId());
        maliciousPayload.put("email", "attaquant@example.com");
        maliciousPayload.put("password", "mot-de-passe-attaquant");
        maliciousPayload.put("firstName", "Attaquant");
        maliciousPayload.put("lastName", "Attaquant");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousPayload)))
                .andReturn();

        int status = result.getResponse().getStatus();
        // Soit la requête réussit en créant un NOUVEL utilisateur (le "id" du payload est
        // ignoré : SignupRequestDto ne porte plus ce champ), soit elle est rejetée (4xx).
        // Dans tous les cas, le compte existant ne doit jamais être altéré.
        if (status >= 200 && status < 300) {
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            long createdId = body.get("user").get("id").asLong();
            assertThat(createdId).isNotEqualTo(existingUser.getId());
        } else {
            assertThat(status).isGreaterThanOrEqualTo(400);
        }

        User reloaded = userDao.findById(existingUser.getId()).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo("victime@example.com");
        assertThat(reloaded.getPassword()).isEqualTo(originalEncryptedPassword);
        assertThat(reloaded.isAdmin()).isFalse();
    }

    // Finding #2 : un client ne doit jamais pouvoir s'auto-attribuer admin=true à l'inscription.
    @Test
    void signup_withAdminTrueInPayload_createsNonAdminUser() throws Exception {
        Map<String, Object> maliciousPayload = new LinkedHashMap<>();
        maliciousPayload.put("email", "wannabe-admin@example.com");
        maliciousPayload.put("password", "mot-de-passe");
        maliciousPayload.put("firstName", "Wannabe");
        maliciousPayload.put("lastName", "Admin");
        maliciousPayload.put("admin", true);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousPayload)))
                .andExpect(status().isCreated());

        User created = userDao.findByEmail("wannabe-admin@example.com").orElseThrow();
        assertThat(created.isAdmin()).isFalse();
    }

    // Finding #3 : un signup avec un email déjà utilisé doit renvoyer 409 (via
    // UserAlreadyExistsException + son handler dédié dans GlobalExceptionHandler), pas 500.
    // Avant correctif, l'exception était avalée par le catch (Exception e) générique de
    // register() et ré-enveloppée en AuthenticationException, qui tombe dans le handler
    // générique -> 500.
    @Test
    void signup_withAlreadyExistingEmail_returns409() throws Exception {
        persistUser("deja-inscrit@example.com", "mot-de-passe-existant", false);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", "deja-inscrit@example.com");
        payload.put("password", "un-autre-mot-de-passe");
        payload.put("firstName", "Nouveau");
        payload.put("lastName", "Venu");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("message").asText()).isEqualTo("Email already exists");
    }

    // Finding #5 : aucune réponse JSON exposant un utilisateur (signup, login, refresh,
    // GET /api/v1/users) ne doit jamais contenir le champ "password".
    @Test
    void authResponses_neverExposePasswordField() throws Exception {
        Map<String, Object> signupPayload = new LinkedHashMap<>();
        signupPayload.put("email", "no-password-leak@example.com");
        signupPayload.put("password", "mot-de-passe");
        signupPayload.put("firstName", "No");
        signupPayload.put("lastName", "Leak");

        MvcResult signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupPayload)))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(signupResult.getResponse().getContentAsString()).doesNotContain("\"password\"");

        Map<String, Object> loginPayload = new LinkedHashMap<>();
        loginPayload.put("email", "no-password-leak@example.com");
        loginPayload.put("password", "mot-de-passe");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginPayload)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(loginResult.getResponse().getContentAsString()).doesNotContain("\"password\"");

        // Utilise le cookie du login, pas celui du signup : login() fait tourner le refresh
        // token en base (un seul refresh token valide par utilisateur à la fois), donc le
        // cookie émis par signup() est déjà périmé à ce stade.
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(refreshResult.getResponse().getContentAsString()).doesNotContain("\"password\"");

        // GET /api/v1/users est désormais réservé à ROLE_ADMIN (AUDIT_BACKEND.md, finding #4) :
        // il faut un token admin pour l'atteindre, ce n'est plus une route publique.
        User admin = persistUser("admin-for-users-check@example.com", "mot-de-passe-admin", true);
        String adminToken = jwtService.generateAccessToken(admin);

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(usersResult.getResponse().getContentAsString()).doesNotContain("\"password\"");
    }
}
