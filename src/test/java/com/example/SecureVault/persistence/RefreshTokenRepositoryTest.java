package com.example.SecureVault.persistence;

import com.example.SecureVault.TestcontainersConfiguration;
import com.example.SecureVault.entity.RefreshToken;
import com.example.SecureVault.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User createUser(String email) {
        return userRepository.save(new User("fullName", email, "passwordHash"));
    }

    private RefreshToken newToken(User user, String hash) {
        return RefreshToken.create(user, hash, Instant.now().plus(Duration.ofDays(7)));
    }

    @Test
    public void findByTokenHash_existingHash_returnsToken() {
        User user = createUser("user@gmail.com");
        RefreshToken saved = refreshTokenRepository.save(newToken(user, "hash"));

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("hash");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    public void findByTokenHash_unknownHash_returnsEmpty() {
        refreshTokenRepository.save(newToken(createUser("user@gmail.com"), "hash"));

        assertThat(refreshTokenRepository.findByTokenHash("other")).isEmpty();
    }

    @Test
    public void findAllByUserIdAndRevokedFalse_returnsOnlyActiveTokensOfUser() {
        User user = createUser("user@gmail.com");
        User other = createUser("other@gmail.com");
        RefreshToken active = refreshTokenRepository.save(newToken(user, "active"));
        RefreshToken revoked = refreshTokenRepository.save(newToken(user, "revoked"));
        revoked.revoke();
        refreshTokenRepository.save(newToken(other, "foreign"));

        List<RefreshToken> found = refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId());

        assertThat(found).extracting(RefreshToken::getId).containsExactly(active.getId());
    }

    @Test
    public void save_duplicateTokenHash_throwsDataIntegrityViolation() {
        User user = createUser("user@gmail.com");
        refreshTokenRepository.saveAndFlush(newToken(user, "hash"));

        assertThatThrownBy(() -> refreshTokenRepository.saveAndFlush(newToken(user, "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
