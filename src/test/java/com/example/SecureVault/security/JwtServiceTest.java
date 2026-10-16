package com.example.SecureVault.security;

import com.example.SecureVault.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JwtServiceTest {

    private static final String SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtZm9yLXNlY3VyZXZhdWx0LXRlc3Rz";
    private static final String OTHER_SECRET = "YW5vdGhlci10ZXN0LW9ubHktc2VjcmV0LXdpdGgtZW5vdWdoLWJ5dGVzIQ";

    private final JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(15));

    private User user() {
        User user = new User("fullName", "user@gmail.com", "passwordHash");
        ReflectionTestUtils.setField(user, "id", 42L);
        return user;
    }

    @Test
    public void generateAccessToken_containsRequiredClaims() {
        Claims claims = jwtService.parseAccessToken(jwtService.generateAccessToken(user()));

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("roles", List.class)).containsExactly("USER");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    public void getAccessTokenTtlSeconds_returns900For15Minutes() {
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    public void parseAccessToken_expiredToken_throwsExpiredJwtException() {
        JwtService expiredJwtService = new JwtService(SECRET, Duration.ofMinutes(-1));
        String token = expiredJwtService.generateAccessToken(user());

        assertThatThrownBy(() -> jwtService.parseAccessToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    public void parseAccessToken_tamperedPayload_throwsJwtException() {
        String[] parts = jwtService.generateAccessToken(user()).split("\\.");
        String fakePayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"1\"}".getBytes());
        String tampered = parts[0] + "." + fakePayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    public void parseAccessToken_signedWithOtherKey_throwsJwtException() {
        String token = new JwtService(OTHER_SECRET, Duration.ofMinutes(15)).generateAccessToken(user());

        assertThatThrownBy(() -> jwtService.parseAccessToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    public void parseAccessToken_malformedToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtService.parseAccessToken("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    public void constructor_keyShorterThan256Bits_throwsWeakKeyException() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new JwtService(shortSecret, Duration.ofMinutes(15))).isInstanceOf(WeakKeyException.class);
    }
}
