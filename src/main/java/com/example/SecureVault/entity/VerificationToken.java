package com.example.SecureVault.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationToken {

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

    private Instant usedAt;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Marks the token as consumed.
     */
    public void markUsed() { this.usedAt = Instant.now(); }

    /**
     * Returns true if the token is unused and not expired.
     */
    public boolean isValid() { return usedAt == null && Instant.now().isBefore(expiresAt); }

    /**
     * Creates a verification token that stores only the hash of the raw token.
     */
    public static VerificationToken create(User user, String tokenHash, Instant expiresAt) {
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.user = user;
        verificationToken.tokenHash = tokenHash;
        verificationToken.expiresAt = expiresAt;
        verificationToken.createdAt = Instant.now();
        return verificationToken;
    }
}
