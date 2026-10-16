package com.example.SecureVault.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenServiceTest {

    private final TokenService tokenService = new TokenService();

    @Test
    public void generateToken_returnsUrlSafeTokenOf32Bytes() {
        String token = tokenService.generateToken();

        assertThat(token).hasSize(43);
        assertThat(token).matches("[A-Za-z0-9_-]+");
    }

    @Test
    public void generateToken_twoCalls_returnDifferentTokens() {
        assertThat(tokenService.generateToken()).isNotEqualTo(tokenService.generateToken());
    }

    @Test
    public void hash_sameInput_returnsSameHash() {
        assertThat(tokenService.hash("token")).isEqualTo(tokenService.hash("token"));
    }

    @Test
    public void hash_returnsSha256HexNotRawToken() {
        String hash = tokenService.hash("token");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
        assertThat(hash).isNotEqualTo("token");
    }
}
