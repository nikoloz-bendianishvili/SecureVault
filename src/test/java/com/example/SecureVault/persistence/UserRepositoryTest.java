package com.example.SecureVault.persistence;

import com.example.SecureVault.TestcontainersConfiguration;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.Roles;
import com.example.SecureVault.enums.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    public void findByEmail_existingEmail_returnsUser() {
        User saved = userRepository.save(new User("fullName", "email@gmail.com", "passwordHash"));

        Optional<User> found = userRepository.findByEmail("email@gmail.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getEmail()).isEqualTo("email@gmail.com");
    }

    @Test
    public void findByEmail_unknownEmail_returnsEmpty() {
        userRepository.save(new User("fullName", "email@gmail.com", "passwordHash"));

        Optional<User> found = userRepository.findByEmail("mail@gmail.com");

        assertThat(found).isEmpty();
    }

    @Test
    public void findByEmail_differentCase_returnsEmpty() {
        userRepository.save(new User("fullName", "email@gmail.com", "passwordHash"));

        Optional<User> found = userRepository.findByEmail("Email@gmail.com");

        assertThat(found).isEmpty();
    }

    @Test
    public void existsByEmail_existingEmail_returnsTrue() {
        userRepository.save(new User("fullName", "email@gmail.com", "passwordHash"));

        assertThat(userRepository.existsByEmail("email@gmail.com")).isTrue();
    }

    @Test
    public void existsByEmail_unknownEmail_returnsFalse() {
        userRepository.save(new User("fullName", "email@gmail.com", "passwordHash"));

        assertThat(userRepository.existsByEmail("mail@gmail.com")).isFalse();
    }

    @Test
    public void save_duplicateEmail_throwsDataIntegrityViolation() {
        userRepository.saveAndFlush(new User("first", "email@gmail.com", "passwordHash"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("second", "email@gmail.com", "passwordHash")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    public void save_newUser_persistsDefaults() {
        User saved = userRepository.saveAndFlush(new User("fullName", "email@gmail.com", "passwordHash"));
        entityManager.clear();

        User found = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getRole()).isEqualTo(Roles.USER);
        assertThat(found.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(found.isEmailVerified()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
