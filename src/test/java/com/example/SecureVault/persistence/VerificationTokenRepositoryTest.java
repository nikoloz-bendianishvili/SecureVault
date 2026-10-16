package com.example.SecureVault.persistence;

import com.example.SecureVault.TestcontainersConfiguration;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.entity.VerificationToken;
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
public class VerificationTokenRepositoryTest {

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User createUser(String email) {
        return userRepository.save(new User("fullName", email, "passwordHash"));
    }

    private VerificationToken newToken(User user, String hash) {
        return VerificationToken.create(user, hash, Instant.now().plus(Duration.ofHours(24)));
    }

    @Test
    public void findByTokenHash_existingHash_returnsToken() {
        User user = createUser("user@gmail.com");
        VerificationToken saved = verificationTokenRepository.save(newToken(user, "hash"));

        Optional<VerificationToken> found = verificationTokenRepository.findByTokenHash("hash");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    public void findByTokenHash_unknownHash_returnsEmpty() {
        verificationTokenRepository.save(newToken(createUser("user@gmail.com"), "hash"));

        assertThat(verificationTokenRepository.findByTokenHash("other")).isEmpty();
    }

    @Test
    public void findAllByUserIdAndUsedAtIsNull_returnsOnlyUnusedTokensOfUser() {
        User user = createUser("user@gmail.com");
        User other = createUser("other@gmail.com");
        VerificationToken unused = verificationTokenRepository.save(newToken(user, "unused"));
        VerificationToken used = verificationTokenRepository.save(newToken(user, "used"));
        used.markUsed();
        verificationTokenRepository.save(newToken(other, "foreign"));

        List<VerificationToken> found = verificationTokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId());

        assertThat(found).extracting(VerificationToken::getId).containsExactly(unused.getId());
    }

    @Test
    public void save_duplicateTokenHash_throwsDataIntegrityViolation() {
        User user = createUser("user@gmail.com");
        verificationTokenRepository.saveAndFlush(newToken(user, "hash"));

        assertThatThrownBy(() -> verificationTokenRepository.saveAndFlush(newToken(user, "hash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
