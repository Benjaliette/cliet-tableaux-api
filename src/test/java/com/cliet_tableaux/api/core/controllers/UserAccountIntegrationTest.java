package com.cliet_tableaux.api.core.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cliet_tableaux.api.core.daos.UserDao;
import com.cliet_tableaux.api.core.dtos.ChangePasswordRequestDto;
import com.cliet_tableaux.api.core.dtos.LoginRequestDto;
import com.cliet_tableaux.api.core.dtos.UpdateProfileRequestDto;
import com.cliet_tableaux.api.core.model.User;
import com.cliet_tableaux.api.core.services.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UserAccountIntegrationTest {

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

    private User persistUser(String email, String rawPassword, String firstName, String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setAdmin(false);
        return userDao.save(user);
    }

    // --- GET /api/v1/users/me ---

    @Test
    void getCurrentUser_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUser_withToken_returnsOwnProfileOnly() throws Exception {
        User self = persistUser("me-profile@example.com", "mot-de-passe", "Alice", "Martin");
        User other = persistUser("other-profile@example.com", "mot-de-passe", "Bob", "Durand");
        String token = jwtService.generateAccessToken(self);

        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isEqualTo(self.getId());
        assertThat(body.get("email").asText()).isEqualTo("me-profile@example.com");
        assertThat(body.get("id").asLong()).isNotEqualTo(other.getId());
    }

    // --- PUT /api/v1/users/me ---

    @Test
    void updateCurrentUser_withoutToken_returns401() throws Exception {
        UpdateProfileRequestDto request = new UpdateProfileRequestDto("Nouveau", "Nom");

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCurrentUser_updatesOnlyOwnAccount_neverAnotherUser() throws Exception {
        User self = persistUser("update-me@example.com", "mot-de-passe", "Ancien", "Nom");
        User victim = persistUser("update-victim@example.com", "mot-de-passe", "Victime", "Intacte");
        String token = jwtService.generateAccessToken(self);

        UpdateProfileRequestDto request = new UpdateProfileRequestDto("Nouveau", "Prenom");

        MvcResult result = mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("firstName").asText()).isEqualTo("Nouveau");
        assertThat(body.get("lastName").asText()).isEqualTo("Prenom");
        assertThat(body.get("id").asLong()).isEqualTo(self.getId());

        User reloadedSelf = userDao.findById(self.getId()).orElseThrow();
        assertThat(reloadedSelf.getFirstName()).isEqualTo("Nouveau");

        User reloadedVictim = userDao.findById(victim.getId()).orElseThrow();
        assertThat(reloadedVictim.getFirstName()).isEqualTo("Victime");
        assertThat(reloadedVictim.getLastName()).isEqualTo("Intacte");
    }

    @Test
    void updateCurrentUser_withBlankFields_returns400() throws Exception {
        User self = persistUser("update-blank@example.com", "mot-de-passe", "Ancien", "Nom");
        String token = jwtService.generateAccessToken(self);

        String invalidBody = "{\"firstName\":\"\",\"lastName\":\"\"}";

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCurrentUser_payloadCannotChangeIdOrAdminFlag() throws Exception {
        User self = persistUser("update-mass-assign@example.com", "mot-de-passe", "Ancien", "Nom");
        String token = jwtService.generateAccessToken(self);

        String maliciousBody = "{\"firstName\":\"Nouveau\",\"lastName\":\"Nom\",\"id\":999999,\"admin\":true}";

        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousBody))
                .andExpect(status().isOk());

        User reloaded = userDao.findById(self.getId()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(self.getId());
        assertThat(reloaded.isAdmin()).isFalse();
    }

    // --- PUT /api/v1/users/me/password ---

    @Test
    void changePassword_withoutToken_returns401() throws Exception {
        ChangePasswordRequestDto request = new ChangePasswordRequestDto("ancien-mdp", "nouveau-mdp-1234");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_withWrongCurrentPassword_isRejected() throws Exception {
        User self = persistUser("change-pwd-wrong@example.com", "mot-de-passe-original", "Prenom", "Nom");
        String token = jwtService.generateAccessToken(self);

        ChangePasswordRequestDto request = new ChangePasswordRequestDto("mauvais-mot-de-passe", "nouveau-mdp-1234");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        User reloaded = userDao.findById(self.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("mot-de-passe-original", reloaded.getPassword())).isTrue();
    }

    @Test
    void changePassword_withCorrectCurrentPassword_updatesPasswordAndAllowsLoginWithNewOne() throws Exception {
        User self = persistUser("change-pwd-ok@example.com", "mot-de-passe-original", "Prenom", "Nom");
        String token = jwtService.generateAccessToken(self);

        ChangePasswordRequestDto request = new ChangePasswordRequestDto("mot-de-passe-original", "nouveau-mdp-1234");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // L'ancien mot de passe ne fonctionne plus
        LoginRequestDto oldLogin = new LoginRequestDto("change-pwd-ok@example.com", "mot-de-passe-original");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // Le nouveau mot de passe fonctionne
        LoginRequestDto newLogin = new LoginRequestDto("change-pwd-ok@example.com", "nouveau-mdp-1234");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_withTooShortNewPassword_returns400() throws Exception {
        User self = persistUser("change-pwd-short@example.com", "mot-de-passe-original", "Prenom", "Nom");
        String token = jwtService.generateAccessToken(self);

        ChangePasswordRequestDto request = new ChangePasswordRequestDto("mot-de-passe-original", "court");

        mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_neverExposesPasswordsInResponse() throws Exception {
        User self = persistUser("change-pwd-no-leak@example.com", "mot-de-passe-original", "Prenom", "Nom");
        String token = jwtService.generateAccessToken(self);

        ChangePasswordRequestDto request = new ChangePasswordRequestDto("mot-de-passe-original", "nouveau-mdp-1234");

        MvcResult result = mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("mot-de-passe-original");
        assertThat(body).doesNotContain("nouveau-mdp-1234");
    }
}
