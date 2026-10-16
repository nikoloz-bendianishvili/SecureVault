package com.example.SecureVault.service;

import com.example.SecureVault.dto.LoginRequestDto;
import com.example.SecureVault.dto.LoginResponseDto;
import com.example.SecureVault.entity.PasswordResetToken;
import com.example.SecureVault.entity.RefreshToken;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.entity.VerificationToken;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.Status;
import com.example.SecureVault.exception.EmailNotVerifiedException;
import com.example.SecureVault.exception.InvalidCredentialsException;
import com.example.SecureVault.exception.InvalidRefreshTokenException;
import com.example.SecureVault.exception.InvalidTokenException;
import com.example.SecureVault.exception.UserDeactivatedException;
import com.example.SecureVault.persistence.PasswordResetTokenRepository;
import com.example.SecureVault.persistence.RefreshTokenRepository;
import com.example.SecureVault.persistence.VerificationTokenRepository;
import com.example.SecureVault.security.JwtService;
import com.example.SecureVault.security.TokenService;
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
public class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private AuditEventService auditEventService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private TokenService tokenService;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AuthService authService;

    private static final Instant FUTURE = Instant.now().plus(Duration.ofHours(1));
    private static final Instant PAST = Instant.now().minus(Duration.ofHours(1));

    private User verifiedUser() {
        User user = new User("fullName", "user@gmail.com", "storedHash");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmailVerified(true);
        return user;
    }

    private LoginRequestDto loginRequest() {
        return new LoginRequestDto("user@gmail.com", "password123");
    }

    @Test
    public void login_unknownEmail_throwsInvalidCredentialsAndAudits() {
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest(), "ip")).isInstanceOf(InvalidCredentialsException.class);
        verify(auditEventService).loginFailure(null, "user@gmail.com", "unknown email", "ip");
    }

    @Test
    public void login_wrongPassword_throwsInvalidCredentials() {
        User user = verifiedUser();
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "storedHash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest(), "ip")).isInstanceOf(InvalidCredentialsException.class);
        verify(auditEventService).loginFailure(1L, "user@gmail.com", "wrong password", "ip");
    }

    @Test
    public void login_unverifiedUser_throwsEmailNotVerified() {
        User user = verifiedUser();
        user.setEmailVerified(false);
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "storedHash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest(), "ip")).isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    public void login_deactivatedUser_throwsUserDeactivated() {
        User user = verifiedUser();
        user.setStatus(Status.DEACTIVATED);
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "storedHash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest(), "ip")).isInstanceOf(UserDeactivatedException.class);
    }

    @Test
    public void login_validCredentials_returnsTokensAndStoresOnlyRefreshTokenHash() {
        User user = verifiedUser();
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "storedHash")).thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("jwt");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(tokenService.generateToken()).thenReturn("rawRefresh");
        when(tokenService.hash("rawRefresh")).thenReturn("refreshHash");

        LoginResponseDto response = authService.login(loginRequest(), "ip");

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.refreshToken()).isEqualTo("rawRefresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("refreshHash");
        verify(auditEventService).record(eq(AuditEventType.LOGIN_SUCCESS), eq(1L), any(), eq(1L), anyString(), eq("ip"));
    }

    @Test
    public void verifyEmail_unknownToken_throwsInvalidTokenAndAudits() {
        when(tokenService.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("raw", "ip")).isInstanceOf(InvalidTokenException.class);
        verify(auditEventService).emailVerificationFailure(null, "unknown token", "ip");
    }

    @Test
    public void verifyEmail_expiredToken_throwsInvalidToken() {
        VerificationToken token = VerificationToken.create(verifiedUser(), "hash", PAST);
        when(tokenService.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("raw", "ip")).isInstanceOf(InvalidTokenException.class);
        verify(auditEventService).emailVerificationFailure(1L, "token expired", "ip");
        verify(userService, never()).changeEmailVerified(any(), any());
    }

    @Test
    public void verifyEmail_alreadyUsedToken_throwsInvalidToken() {
        VerificationToken token = VerificationToken.create(verifiedUser(), "hash", FUTURE);
        token.markUsed();
        when(tokenService.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.verifyEmail("raw", "ip")).isInstanceOf(InvalidTokenException.class);
        verify(auditEventService).emailVerificationFailure(1L, "token already used", "ip");
    }

    @Test
    public void verifyEmail_validToken_marksTokenUsedAndVerifiesUser() {
        VerificationToken token = VerificationToken.create(verifiedUser(), "hash", FUTURE);
        when(tokenService.hash("raw")).thenReturn("hash");
        when(verificationTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        authService.verifyEmail("raw", "ip");

        assertThat(token.getUsedAt()).isNotNull();
        verify(userService).changeEmailVerified(1L, "ip");
    }

    @Test
    public void resendVerification_unverifiedUser_invalidatesOldTokensAndSendsNewOne() {
        User user = verifiedUser();
        user.setEmailVerified(false);
        VerificationToken oldToken = VerificationToken.create(user, "oldHash", FUTURE);
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(verificationTokenRepository.findAllByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of(oldToken));
        when(tokenService.generateToken()).thenReturn("newRaw");
        when(tokenService.hash("newRaw")).thenReturn("newHash");

        authService.resendVerification("user@gmail.com", "ip");

        assertThat(oldToken.getUsedAt()).isNotNull();
        verify(emailService).sendVerificationEmail("user@gmail.com", "newRaw");
    }

    @Test
    public void resendVerification_unknownEmail_sendsNothing() {
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.empty());

        authService.resendVerification("user@gmail.com", "ip");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    public void resendVerification_alreadyVerifiedUser_sendsNothing() {
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.of(verifiedUser()));

        authService.resendVerification("user@gmail.com", "ip");

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    public void refresh_activeToken_revokesOldTokenAndIssuesNewPair() {
        User user = verifiedUser();
        RefreshToken oldToken = RefreshToken.create(user, "oldHash", FUTURE);
        when(tokenService.hash("oldRaw")).thenReturn("oldHash");
        when(refreshTokenRepository.findByTokenHash("oldHash")).thenReturn(Optional.of(oldToken));
        when(tokenService.generateToken()).thenReturn("newRaw");
        when(tokenService.hash("newRaw")).thenReturn("newHash");
        when(jwtService.generateAccessToken(user)).thenReturn("jwt");

        LoginResponseDto response = authService.refresh("oldRaw", "ip");

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(response.refreshToken()).isEqualTo("newRaw");
        assertThat(response.accessToken()).isEqualTo("jwt");
    }

    @Test
    public void refresh_revokedToken_revokesAllUserTokensAndThrows() {
        User user = verifiedUser();
        RefreshToken reused = RefreshToken.create(user, "reusedHash", FUTURE);
        reused.revoke();
        RefreshToken stillActive = RefreshToken.create(user, "activeHash", FUTURE);
        when(tokenService.hash("reusedRaw")).thenReturn("reusedHash");
        when(refreshTokenRepository.findByTokenHash("reusedHash")).thenReturn(Optional.of(reused));
        when(refreshTokenRepository.findAllByUserIdAndRevokedFalse(1L)).thenReturn(List.of(stillActive));

        assertThatThrownBy(() -> authService.refresh("reusedRaw", "ip")).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(stillActive.isRevoked()).isTrue();
        verify(auditEventService).record(eq(AuditEventType.REFRESH_TOKEN_REUSE_DETECTED), eq(1L), any(), eq(1L), anyString(), eq("ip"));
    }

    @Test
    public void refresh_expiredToken_throws() {
        RefreshToken expired = RefreshToken.create(verifiedUser(), "hash", PAST);
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw", "ip")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    public void refresh_unknownToken_throws() {
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("raw", "ip")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    public void refresh_deactivatedUser_throws() {
        User user = verifiedUser();
        user.setStatus(Status.DEACTIVATED);
        RefreshToken token = RefreshToken.create(user, "hash", FUTURE);
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refresh("raw", "ip")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    public void logout_activeToken_revokesToken() {
        RefreshToken token = RefreshToken.create(verifiedUser(), "hash", FUTURE);
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        authService.logout("raw", "ip");

        assertThat(token.isRevoked()).isTrue();
        verify(auditEventService).record(eq(AuditEventType.LOGOUT), eq(1L), any(), eq(1L), anyString(), eq("ip"));
    }

    @Test
    public void logout_unknownToken_doesNothing() {
        when(tokenService.hash("raw")).thenReturn("hash");
        when(refreshTokenRepository.findByTokenHash("hash")).thenReturn(Optional.empty());

        authService.logout("raw", "ip");

        verify(auditEventService, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void forgotPassword_unknownEmail_sendsNothing() {
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.empty());

        authService.forgotPassword("user@gmail.com", "ip");

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    public void forgotPassword_knownEmail_invalidatesOldTokensAndSendsNewOne() {
        User user = verifiedUser();
        PasswordResetToken oldToken = PasswordResetToken.create(user, "oldHash", FUTURE);
        when(userService.getByEmail("user@gmail.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findAllByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of(oldToken));
        when(tokenService.generateToken()).thenReturn("raw");
        when(tokenService.hash("raw")).thenReturn("hash");

        authService.forgotPassword("user@gmail.com", "ip");

        assertThat(oldToken.getUsedAt()).isNotNull();
        verify(emailService).sendPasswordResetEmail("user@gmail.com", "raw");
        verify(auditEventService).record(eq(AuditEventType.PASSWORD_RESET_REQUESTED), eq(1L), any(), eq(1L), anyString(), eq("ip"));
    }

    @Test
    public void resetPassword_validToken_marksUsedAndChangesPassword() {
        PasswordResetToken token = PasswordResetToken.create(verifiedUser(), "hash", FUTURE);
        when(tokenService.hash("raw")).thenReturn("hash");
        when(passwordResetTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        authService.resetPassword("raw", "newPassword", "ip");

        assertThat(token.getUsedAt()).isNotNull();
        verify(userService).changePassword(1L, "newPassword", "ip");
    }

    @Test
    public void resetPassword_expiredToken_throwsInvalidToken() {
        PasswordResetToken token = PasswordResetToken.create(verifiedUser(), "hash", PAST);
        when(tokenService.hash("raw")).thenReturn("hash");
        when(passwordResetTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("raw", "newPassword", "ip")).isInstanceOf(InvalidTokenException.class);
        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    public void resetPassword_usedToken_throwsInvalidToken() {
        PasswordResetToken token = PasswordResetToken.create(verifiedUser(), "hash", FUTURE);
        token.markUsed();
        when(tokenService.hash("raw")).thenReturn("hash");
        when(passwordResetTokenRepository.findByTokenHash("hash")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword("raw", "newPassword", "ip")).isInstanceOf(InvalidTokenException.class);
    }
}
