package com.example.SecureVault.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

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
     * Creates a password-reset token that stores only the hash of the raw token.
     */
    public static PasswordResetToken create(User user, String tokenHash, Instant expiresAt) {
        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.user = user;
        passwordResetToken.tokenHash = tokenHash;
        passwordResetToken.expiresAt = expiresAt;
        passwordResetToken.createdAt = Instant.now();
        return passwordResetToken;
    }
}
