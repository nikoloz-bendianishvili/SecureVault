package com.example.SecureVault.api;

import com.example.SecureVault.entity.PasswordResetToken;
import com.example.SecureVault.persistence.PasswordResetTokenRepository;
import com.example.SecureVault.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PasswordResetIntegrationTest extends IntegrationTestBase {

    private static final String NEW_PASSWORD = "newPassword123";

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private TokenService tokenService;

    private ResultActions forgotPassword(String email) throws Exception {
        return postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(email));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return postJson("/api/v1/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword));
    }

    @Test
    public void forgotPassword_unknownEmail_returnsSameGenericResponse() throws Exception {
        forgotPassword(uniqueEmail()).andExpect(status().isOk());

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    public void forgotPassword_knownEmail_returns200AndSendsEmail() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);

        forgotPassword(email).andExpect(status().isOk());

        verify(emailService).sendPasswordResetEmail(eq(email), anyString());
    }

    @Test
    public void resetPassword_validToken_newPasswordWorksOldPasswordDoesNot() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        forgotPassword(email);

        resetPassword(lastPasswordResetToken(email), NEW_PASSWORD).andExpect(status().isOk());

        loginRequest(email, NEW_PASSWORD).andExpect(status().isOk());
        loginRequest(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    public void resetPassword_success_revokesExistingRefreshTokens() throws Exception {
        String email = uniqueEmail();
        Tokens tokens = createVerifiedUser(email);
        forgotPassword(email);

        resetPassword(lastPasswordResetToken(email), NEW_PASSWORD).andExpect(status().isOk());

        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(tokens.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void resetPassword_tokenUsedTwice_secondReturns400() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        forgotPassword(email);
        String token = lastPasswordResetToken(email);
        resetPassword(token, NEW_PASSWORD).andExpect(status().isOk());

        resetPassword(token, "anotherPassword1").andExpect(status().isBadRequest());
    }

    @Test
    public void resetPassword_expiredToken_returns400() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        passwordResetTokenRepository.save(PasswordResetToken.create(
                findUser(email), tokenService.hash("expired-reset"), Instant.now().minus(Duration.ofMinutes(1))));

        resetPassword("expired-reset", NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    public void resetPassword_invalidToken_returns400() throws Exception {
        resetPassword("invalid-token", NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    public void resetPassword_tooShortPassword_returns400() throws Exception {
        resetPassword("any-token", "short").andExpect(status().isBadRequest());
    }
}
