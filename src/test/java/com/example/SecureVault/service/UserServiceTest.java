package com.example.SecureVault.service;

import com.example.SecureVault.dto.RegisterRequestDto;
import com.example.SecureVault.entity.RefreshToken;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.Roles;
import com.example.SecureVault.enums.Status;
import com.example.SecureVault.exception.AdminNotChangingThemselvesException;
import com.example.SecureVault.exception.EmailAlreadyExistsException;
import com.example.SecureVault.exception.UserNotFoundException;
import com.example.SecureVault.persistence.RefreshTokenRepository;
import com.example.SecureVault.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditEventService auditEventService;

    @InjectMocks
    private UserService userService;

    private User user(Long id) {
        User user = new User("fullName", "user" + id + "@gmail.com", "oldHash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    public void create_duplicateEmail_throwsEmailAlreadyExists() {
        when(userRepository.existsByEmail("user@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new RegisterRequestDto("fullName", "user@gmail.com", "password123"), "ip"))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    public void create_newUser_storesEncodedPasswordNotPlaintext() {
        when(userRepository.existsByEmail("user@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedHash");

        userService.create(new RegisterRequestDto("fullName", "user@gmail.com", "password123"), "ip");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encodedHash");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(captor.getValue().getRole()).isEqualTo(Roles.USER);
        assertThat(captor.getValue().isEmailVerified()).isFalse();
    }

    @Test
    public void create_newUser_auditsRegistration() {
        when(userRepository.existsByEmail("user@gmail.com")).thenReturn(false);

        userService.create(new RegisterRequestDto("fullName", "user@gmail.com", "password123"), "ip");

        verify(auditEventService).record(eq(AuditEventType.USER_REGISTERED), any(), any(), any(), anyString(), eq("ip"));
    }

    @Test
    public void getById_unknownUser_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    public void changeRole_adminChangesOwnRole_throws() {
        assertThatThrownBy(() -> userService.changeRole(1L, 1L, Roles.USER, "ip"))
                .isInstanceOf(AdminNotChangingThemselvesException.class);
    }

    @Test
    public void changeRole_otherUser_changesRoleAndAudits() {
        User user = user(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.changeRole(1L, 2L, Roles.MODERATOR, "ip");

        assertThat(user.getRole()).isEqualTo(Roles.MODERATOR);
        verify(auditEventService).record(eq(AuditEventType.ROLE_CHANGED), eq(1L), any(), eq(2L), eq("USER -> MODERATOR"), eq("ip"));
    }

    @Test
    public void changeStatus_adminChangesOwnStatus_throws() {
        assertThatThrownBy(() -> userService.changeStatus(1L, 1L, Status.DEACTIVATED, "ip"))
                .isInstanceOf(AdminNotChangingThemselvesException.class);
    }

    @Test
    public void changeStatus_deactivate_revokesAllRefreshTokens() {
        User user = user(2L);
        RefreshToken token = RefreshToken.create(user, "hash", Instant.now().plus(Duration.ofDays(7)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(2L)).thenReturn(List.of(token));

        userService.changeStatus(1L, 2L, Status.DEACTIVATED, "ip");

        assertThat(user.getStatus()).isEqualTo(Status.DEACTIVATED);
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    public void changeStatus_activate_doesNotRevokeTokens() {
        User user = user(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.changeStatus(1L, 2L, Status.ACTIVE, "ip");

        verify(refreshTokenRepository, never()).findAllByUserIdAndRevokedFalse(any());
    }

    @Test
    public void changePassword_encodesNewPasswordAndRevokesRefreshTokens() {
        User user = user(2L);
        RefreshToken token = RefreshToken.create(user, "hash", Instant.now().plus(Duration.ofDays(7)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword")).thenReturn("newHash");
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(2L)).thenReturn(List.of(token));

        userService.changePassword(2L, "newPassword", "ip");

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
        assertThat(token.isRevoked()).isTrue();
        verify(auditEventService).record(eq(AuditEventType.PASSWORD_CHANGED), eq(2L), any(), eq(2L), anyString(), eq("ip"));
    }

    @Test
    public void changeEmailVerified_setsVerifiedAndAudits() {
        User user = user(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        userService.changeEmailVerified(2L, "ip");

        assertThat(user.isEmailVerified()).isTrue();
        verify(auditEventService).record(eq(AuditEventType.EMAIL_VERIFIED), eq(2L), any(), eq(2L), anyString(), eq("ip"));
    }
}
