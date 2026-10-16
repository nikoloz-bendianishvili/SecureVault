package com.example.SecureVault.persistence;

import com.example.SecureVault.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Returns all non-revoked refresh tokens of a user.
     */
    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);
}
