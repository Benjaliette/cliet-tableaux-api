package com.cliet_tableaux.api.core.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.cliet_tableaux.api.core.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "CeciEstUneCleSecreteDeTestSuperLongueEtBidonPourLesTestsUnitaires123456");
    }

    private User aUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hashed-password");
        return user;
    }

    @Test
    void isTokenValid_withAccessToken_returnsTrue() {
        User user = aUser("access-token-user@example.com");
        String accessToken = jwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(accessToken, user)).isTrue();
    }

    @Test
    void isTokenValid_withRefreshToken_returnsFalse() {
        User user = aUser("refresh-token-user@example.com");
        String refreshToken = jwtService.generateRefreshToken(user);

        assertThat(jwtService.isTokenValid(refreshToken, user)).isFalse();
    }

    @Test
    void extractTokenType_distinguishesAccessAndRefresh() {
        User user = aUser("type-claim-user@example.com");

        assertThat(jwtService.extractTokenType(jwtService.generateAccessToken(user))).isEqualTo("access");
        assertThat(jwtService.extractTokenType(jwtService.generateRefreshToken(user))).isEqualTo("refresh");
    }
}
