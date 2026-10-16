package com.example.SecureVault.persistence;

import com.example.SecureVault.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Returns all unused password-reset tokens of a user.
     */
    List<PasswordResetToken> findAllByUserIdAndUsedAtIsNull(Long userId);
}
