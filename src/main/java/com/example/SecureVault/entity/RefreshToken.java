package com.example.SecureVault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    private boolean revoked;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Marks the token as revoked so it can no longer be used.
     */
    public void revoke() {this.revoked = true;}

    /**
     * Returns true if the token is neither revoked nor expired.
     */
    public boolean isActive() {return !revoked && Instant.now().isBefore(expiresAt);}

    /**
     * Creates a refresh token that stores only the hash of the raw token.
     */
    public static RefreshToken create(User user, String tokenHash, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.tokenHash = tokenHash;
        refreshToken.expiresAt = expiresAt;
        refreshToken.revoked = false;
        refreshToken.createdAt = Instant.now();
        return refreshToken;
    }
}
