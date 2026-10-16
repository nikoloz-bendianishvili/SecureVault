package com.example.SecureVault.persistence;

import com.example.SecureVault.TestcontainersConfiguration;
import com.example.SecureVault.entity.PasswordResetToken;
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
public class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User createUser(String email) {
        return userRepository.save(new User("fullName", email, "passwordHash"));
    }

    private PasswordResetToken newToken(User user, String hash) {
        return PasswordResetToken.create(user, hash, Instant.now().plus(Duration.ofHours(1)));
    }

    @Test
    public void findByTokenHash_existingHash_returnsToken() {
        User user = createUser("user@gmail.com");
        PasswordResetToken saved = passwordResetTokenRepository.save(newToken(user, "hash"));

        Optional<PasswordResetToken> found = passwordResetTokenRepository.findByTokenHash("hash");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    public void findByTokenHash_unknownHash_returnsEmpty() {
        passwordResetTokenRepository.save(newToken(createUser("user@gmail.com"), "hash"));

        assertThat(passwordResetTokenRepository.findByTokenHash("other")).isEmpty();
    }

    @Test
    public void findAllByUserIdAndUsedAtIsNull_returnsOnlyUnusedTokensOfUser() {
        User user = createUser("user@gmail.com");
        User other = createUser("other@gmail.com");
        PasswordResetToken unused = passwordResetTokenRepository.save(newToken(user, "unused"));
        PasswordResetToken used = passwordResetTokenRepository.save(newToken(user, "used"));
        used.markUsed();
        passwordResetTokenRepository.save(newToken(other, "foreign"));

        List<PasswordResetToken> found = passwordResetTokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId());

        assertThat(found).extracting(PasswordResetToken::getId).containsExactly(unused.getId());
    }

    @Test
    public void save_duplicateTokenHash_throwsDataIntegrityViolation() {
        User user = createUser("user@gmail.com");
        passwordResetTokenRepository.saveAndFlush(newToken(user, "hash"));

        assertThatThrownBy(() -> passwordResetTokenRepository.saveAndFlush(newToken(user, "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
