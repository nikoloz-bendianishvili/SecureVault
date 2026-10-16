package com.example.SecureVault.api;

import com.example.SecureVault.TestcontainersConfiguration;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.persistence.UserRepository;
import com.example.SecureVault.service.EmailService;
import com.jayway.jsonpath.JsonPath;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "password123";

    @Value("${app.admin.email}")
    protected String adminEmail;

    @Value("${app.admin.password}")
    protected String adminPassword;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @MockitoBean
    protected EmailService emailService;

    protected record Tokens(String accessToken, String refreshToken) {}

    protected String uniqueEmail() {
        return UUID.randomUUID() + "@test.com";
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions register(String email, String password) throws Exception {
        return postJson("/api/v1/auth/register",
                "{\"fullName\":\"Test User\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    protected String lastVerificationToken(String email) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendVerificationEmail(eq(email), captor.capture());
        return captor.getValue();
    }

    protected String lastPasswordResetToken(String email) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendPasswordResetEmail(eq(email), captor.capture());
        return captor.getValue();
    }

    protected ResultActions verifyEmail(String token) throws Exception {
        return postJson("/api/v1/auth/verify-email", "{\"token\":\"%s\"}".formatted(token));
    }

    protected ResultActions loginRequest(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    protected Tokens login(String email, String password) throws Exception {
        String body = loginRequest(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
    }

    protected Tokens createVerifiedUser(String email) throws Exception {
        register(email, PASSWORD).andExpect(status().isCreated());
        verifyEmail(lastVerificationToken(email)).andExpect(status().isOk());
        return login(email, PASSWORD);
    }

    protected Tokens loginAsAdmin() throws Exception {
        return login(adminEmail, adminPassword);
    }

    protected String bearer(Tokens tokens) {
        return "Bearer " + tokens.accessToken();
    }

    protected User findUser(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    protected Long idFrom(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
