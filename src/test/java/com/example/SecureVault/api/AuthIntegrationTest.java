package com.example.SecureVault.api;

import com.example.SecureVault.entity.User;
import com.example.SecureVault.entity.VerificationToken;
import com.example.SecureVault.enums.Status;
import com.example.SecureVault.persistence.VerificationTokenRepository;
import com.example.SecureVault.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    public void register_validRequest_returns201AndStoresHashedPassword() throws Exception {
        String email = uniqueEmail();

        register(email, PASSWORD).andExpect(status().isCreated());

        User user = findUser(email);
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    public void register_invalidFields_returns400WithFieldErrors() throws Exception {
        postJson("/api/v1/auth/register", "{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    public void register_wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"fullName\":\"Test User\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    public void register_malformedJson_returns400() throws Exception {
        postJson("/api/v1/auth/register", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    public void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());

        register(email, PASSWORD).andExpect(status().isConflict());
    }

    @Test
    public void verifyEmail_validToken_returns200AndVerifiesUser() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        verifyEmail(lastVerificationToken(email)).andExpect(status().isOk());

        assertThat(findUser(email).isEmailVerified()).isTrue();
    }

    @Test
    public void verifyEmail_alreadyUsedToken_returns400() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        String token = lastVerificationToken(email);
        verifyEmail(token).andExpect(status().isOk());

        verifyEmail(token).andExpect(status().isBadRequest());
    }

    @Test
    public void verifyEmail_invalidToken_returns400() throws Exception {
        verifyEmail("invalid-token").andExpect(status().isBadRequest());
    }

    @Test
    public void verifyEmail_expiredToken_returns400() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        verificationTokenRepository.save(VerificationToken.create(
                findUser(email), tokenService.hash("expired-token"), Instant.now().minus(Duration.ofHours(1))));

        verifyEmail("expired-token").andExpect(status().isBadRequest());
    }

    @Test
    public void resendVerification_unverifiedUser_oldTokenStopsWorkingNewTokenWorks() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        String oldToken = lastVerificationToken(email);

        postJson("/api/v1/auth/resend-verification", "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isOk());
        String newToken = lastVerificationToken(email);

        verifyEmail(oldToken).andExpect(status().isBadRequest());
        verifyEmail(newToken).andExpect(status().isOk());
    }

    @Test
    public void resendVerification_unknownEmail_returnsSameGenericResponse() throws Exception {
        postJson("/api/v1/auth/resend-verification", "{\"email\":\"%s\"}".formatted(uniqueEmail()))
                .andExpect(status().isOk());

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    public void login_validCredentials_returnsTokens() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        verifyEmail(lastVerificationToken(email));

        loginRequest(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    public void login_wrongPassword_returns401() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);

        loginRequest(email, "wrongPassword").andExpect(status().isUnauthorized());
    }

    @Test
    public void login_unknownUser_returns401WithSameMessage() throws Exception {
        loginRequest(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    public void login_unverifiedUser_returns403() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        loginRequest(email, PASSWORD).andExpect(status().isForbidden());
    }

    @Test
    public void login_deactivatedUser_returns403() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        User user = findUser(email);
        user.setStatus(Status.DEACTIVATED);
        userRepository.save(user);

        loginRequest(email, PASSWORD).andExpect(status().isForbidden());
    }
}
