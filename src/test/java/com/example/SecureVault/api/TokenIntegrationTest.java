package com.example.SecureVault.api;

import com.example.SecureVault.entity.RefreshToken;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.Status;
import com.example.SecureVault.persistence.RefreshTokenRepository;
import com.example.SecureVault.security.JwtService;
import com.example.SecureVault.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TokenIntegrationTest extends IntegrationTestBase {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenService tokenService;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(refreshToken));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/logout", "{\"token\":\"%s\"}".formatted(refreshToken));
    }

    private ResultActions getDocuments(String authorization) throws Exception {
        return mockMvc.perform(get("/api/v1/documents").header(HttpHeaders.AUTHORIZATION, authorization));
    }

    @Test
    public void refresh_validToken_returnsNewTokenPair() throws Exception {
        Tokens tokens = createVerifiedUser(uniqueEmail());

        String body = refresh(tokens.refreshToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String newRefreshToken = JsonPath.read(body, "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(tokens.refreshToken());
        assertThat((String) JsonPath.read(body, "$.accessToken")).isNotBlank();
    }

    @Test
    public void refresh_oldTokenAfterRotation_returns401() throws Exception {
        Tokens tokens = createVerifiedUser(uniqueEmail());
        refresh(tokens.refreshToken()).andExpect(status().isOk());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    public void refresh_reusedToken_revokesWholeSessionIncludingNewToken() throws Exception {
        Tokens tokens = createVerifiedUser(uniqueEmail());
        String body = refresh(tokens.refreshToken()).andReturn().getResponse().getContentAsString();
        String newRefreshToken = JsonPath.read(body, "$.refreshToken");

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());

        refresh(newRefreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    public void refresh_expiredToken_returns401() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        refreshTokenRepository.save(RefreshToken.create(
                findUser(email), tokenService.hash("expired-refresh"), Instant.now().minus(Duration.ofMinutes(1))));

        refresh("expired-refresh").andExpect(status().isUnauthorized());
    }

    @Test
    public void refresh_unknownToken_returns401() throws Exception {
        refresh("unknown-token").andExpect(status().isUnauthorized());
    }

    @Test
    public void logout_revokesRefreshTokenSoItCannotBeUsed() throws Exception {
        Tokens tokens = createVerifiedUser(uniqueEmail());

        logout(tokens.refreshToken()).andExpect(status().isNoContent());

        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    public void logout_calledTwice_returns204Both() throws Exception {
        Tokens tokens = createVerifiedUser(uniqueEmail());

        logout(tokens.refreshToken()).andExpect(status().isNoContent());
        logout(tokens.refreshToken()).andExpect(status().isNoContent());
    }

    @Test
    public void protectedEndpoint_validAccessToken_returns200() throws Exception {
        Tokens tokens = createVerifiedUser(uniqueEmail());

        getDocuments(bearer(tokens)).andExpect(status().isOk());
    }

    @Test
    public void protectedEndpoint_noToken_returns401WithErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/documents"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    public void protectedEndpoint_malformedToken_returns401() throws Exception {
        getDocuments("Bearer not-a-jwt").andExpect(status().isUnauthorized());
    }

    @Test
    public void protectedEndpoint_tamperedToken_returns401() throws Exception {
        Tokens tokens = createVerifiedUser(uniqueEmail());
        String[] parts = tokens.accessToken().split("\\.");
        String fakePayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\"}".getBytes());

        getDocuments("Bearer " + parts[0] + "." + fakePayload + "." + parts[2]).andExpect(status().isUnauthorized());
    }

    @Test
    public void protectedEndpoint_expiredToken_returns401() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        String expiredToken = new JwtService(jwtSecret, Duration.ofMinutes(-1)).generateAccessToken(findUser(email));

        getDocuments("Bearer " + expiredToken).andExpect(status().isUnauthorized());
    }

    @Test
    public void protectedEndpoint_userDeactivatedAfterLogin_returns401() throws Exception {
        String email = uniqueEmail();
        Tokens tokens = createVerifiedUser(email);
        User user = findUser(email);
        user.setStatus(Status.DEACTIVATED);
        userRepository.save(user);

        getDocuments(bearer(tokens)).andExpect(status().isUnauthorized());
    }
}
