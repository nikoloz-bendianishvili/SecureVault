package com.example.SecureVault.security;

import com.example.SecureVault.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and validates HS256-signed JWT access tokens.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    /**
     * @param secret Base64-encoded signing key of at least 256 bits
     * @param accessTokenTtl lifetime of issued access tokens
     */
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenTtl = accessTokenTtl;
    }

    /**
     * Issues an access token with the claims sub (user id), roles, iat, exp and jti.
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("roles", List.of(user.getRole().name()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies the signature and expiry of an access token and returns its claims.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered with or expired
     */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Returns the access-token lifetime in seconds.
     */
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }
}