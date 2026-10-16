package com.example.SecureVault.api;

import com.example.SecureVault.entity.AuditEvent;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.Roles;
import com.example.SecureVault.enums.Status;
import com.example.SecureVault.persistence.AuditEventRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AdminIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuditEventRepository auditEventRepository;

    private ResultActions getUsers(Tokens tokens) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(tokens)));
    }

    private ResultActions changeRole(Tokens tokens, Long userId, String role) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/role")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"%s\"}".formatted(role)));
    }

    private ResultActions changeStatus(Tokens tokens, Long userId, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/admin/users/" + userId + "/status")
                .header(HttpHeaders.AUTHORIZATION, bearer(tokens))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\"}".formatted(status)));
    }

    private ResultActions getAuditEvents(Tokens tokens, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/audit-events" + query).header(HttpHeaders.AUTHORIZATION, bearer(tokens)));
    }

    private List<AuditEvent> eventsOf(Long userId) {
        return auditEventRepository.findAll().stream().filter(event -> userId.equals(event.getActorUserId())).toList();
    }

    @Test
    public void adminEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    public void adminEndpoint_asUser_returns403WithErrorBody() throws Exception {
        Tokens user = createVerifiedUser(uniqueEmail());

        getUsers(user)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/users"));
    }

    @Test
    public void adminEndpoint_asModerator_returns403() throws Exception {
        String email = uniqueEmail();
        Tokens moderator = createVerifiedUser(email);
        User user = findUser(email);
        user.setRole(Roles.MODERATOR);
        userRepository.save(user);

        getUsers(moderator).andExpect(status().isForbidden());
        getAuditEvents(moderator, "").andExpect(status().isForbidden());
    }

    @Test
    public void getUsers_asAdmin_returns200WithoutPasswordHashes() throws Exception {
        getUsers(loginAsAdmin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    public void changeRole_asAdmin_returns204AndUpdatesRole() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        Long userId = findUser(email).getId();

        changeRole(loginAsAdmin(), userId, "MODERATOR").andExpect(status().isNoContent());

        assertThat(findUser(email).getRole()).isEqualTo(Roles.MODERATOR);
    }

    @Test
    public void changeRole_adminChangesOwnRole_returns400() throws Exception {
        changeRole(loginAsAdmin(), findUser(adminEmail).getId(), "USER").andExpect(status().isBadRequest());
    }

    @Test
    public void changeRole_invalidRole_returns400() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);

        changeRole(loginAsAdmin(), findUser(email).getId(), "SUPERUSER").andExpect(status().isBadRequest());
    }

    @Test
    public void changeStatus_deactivate_userCannotLoginOrRefresh() throws Exception {
        String email = uniqueEmail();
        Tokens tokens = createVerifiedUser(email);

        changeStatus(loginAsAdmin(), findUser(email).getId(), "DEACTIVATED").andExpect(status().isNoContent());

        assertThat(findUser(email).getStatus()).isEqualTo(Status.DEACTIVATED);
        loginRequest(email, PASSWORD).andExpect(status().isForbidden());
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(tokens.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void auditEvents_asUser_returns403() throws Exception {
        getAuditEvents(createVerifiedUser(uniqueEmail()), "").andExpect(status().isForbidden());
    }

    @Test
    public void auditEvents_asAdmin_returnsRequestedPageNewestFirst() throws Exception {
        createVerifiedUser(uniqueEmail());

        String body = getAuditEvents(loginAsAdmin(), "?page=0&size=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andReturn().getResponse().getContentAsString();

        List<Number> ids = JsonPath.read(body, "$.content[*].id");
        List<Long> idValues = ids.stream().map(Number::longValue).toList();
        assertThat(idValues).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    public void auditEvents_keyActionsAreRecorded() throws Exception {
        String email = uniqueEmail();
        createVerifiedUser(email);
        Long userId = findUser(email).getId();

        List<AuditEventType> types = eventsOf(userId).stream().map(AuditEvent::getEventType).toList();

        assertThat(types).contains(AuditEventType.USER_REGISTERED, AuditEventType.EMAIL_VERIFIED, AuditEventType.LOGIN_SUCCESS);
    }

    @Test
    public void auditEvents_neverContainPasswordsOrRawTokens() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        String verificationToken = lastVerificationToken(email);
        verifyEmail(verificationToken);
        Tokens tokens = login(email, PASSWORD);
        postJson("/api/v1/auth/forgot-password", "{\"email\":\"%s\"}".formatted(email));
        String resetToken = lastPasswordResetToken(email);
        postJson("/api/v1/auth/reset-password", "{\"token\":\"%s\",\"newPassword\":\"newPassword123\"}".formatted(resetToken));
        loginRequest(email, "wrongPassword99");

        List<String> secrets = List.of(PASSWORD, "newPassword123", "wrongPassword99",
                verificationToken, resetToken, tokens.accessToken(), tokens.refreshToken());

        for (AuditEvent event : auditEventRepository.findAll()) {
            String stored = event.getDetails() + " " + event.getIpAddress();
            for (String secret : secrets) {
                assertThat(stored).doesNotContain(secret);
            }
        }
    }
}
