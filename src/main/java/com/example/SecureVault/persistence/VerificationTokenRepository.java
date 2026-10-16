package com.example.SecureVault.persistence;

import com.example.SecureVault.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /**
     * Returns all unused verification tokens of a user.
     */
    List<VerificationToken> findAllByUserIdAndUsedAtIsNull(Long userId);
}
