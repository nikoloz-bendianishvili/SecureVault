package com.example.SecureVault.service;

import com.example.SecureVault.dto.LoginRequestDto;
import com.example.SecureVault.dto.LoginResponseDto;
import com.example.SecureVault.dto.RegisterRequestDto;
import com.example.SecureVault.entity.PasswordResetToken;
import com.example.SecureVault.entity.RefreshToken;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.entity.VerificationToken;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.AuditTargetType;
import com.example.SecureVault.enums.Status;
import com.example.SecureVault.exception.*;
import com.example.SecureVault.persistence.PasswordResetTokenRepository;
import com.example.SecureVault.persistence.RefreshTokenRepository;
import com.example.SecureVault.persistence.VerificationTokenRepository;
import com.example.SecureVault.security.JwtService;
import com.example.SecureVault.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Authentication flows: registration, email verification, login, token refresh, logout and password reset.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final AuditEventService auditEventService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TokenService tokenService;
    private final VerificationTokenRepository verificationTokenRepository;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofHours(1);

    /**
     * Creates an unverified user and emails a verification link.
     */
    @Transactional
    public void register(RegisterRequestDto registerRequestDto, String ipAddress) {
        User user = userService.create(registerRequestDto, ipAddress);
        sendNewVerificationToken(user);
    }

    /**
     * Marks the user's email as verified using a single-use verification token.
     *
     * @throws InvalidTokenException if the token is unknown, expired or already used
     */
    @Transactional
    public void verifyEmail(String rawToken, String ipAddress) {
        Optional<VerificationToken> token = verificationTokenRepository.findByTokenHash(tokenService.hash(rawToken));

        if (token.isEmpty()) {
            auditEventService.emailVerificationFailure(null, "unknown token", ipAddress);
            throw new InvalidTokenException("Invalid or expired verification token");
        }

        if (!token.get().isValid()) {
            String reason = token.get().getUsedAt() != null ? "token already used" : "token expired";
            auditEventService.emailVerificationFailure(token.get().getUser().getId(), reason, ipAddress);
            throw new InvalidTokenException("Invalid or expired verification token");
        }

        token.get().markUsed();
        userService.changeEmailVerified(token.get().getUser().getId(), ipAddress);
    }

    /**
     * Invalidates outstanding verification tokens and sends a new one.
     * Completes silently for unknown or already verified emails to prevent account enumeration.
     */
    @Transactional
    public void resendVerification(String email, String ipAddress) {
        userService.getByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(user -> {
                    verificationTokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId())
                            .forEach(VerificationToken::markUsed);
                    sendNewVerificationToken(user);
                    auditEventService.record(AuditEventType.VERIFICATION_RESENT, user.getId(), AuditTargetType.USER, user.getId(), "Verification email resent", ipAddress);
                }, () -> auditEventService.record(AuditEventType.VERIFICATION_RESENT, null, null, null, "email=" + email + ", no unverified account, nothing sent", ipAddress));
    }

    private void sendNewVerificationToken(User user) {
        String rawToken = tokenService.generateToken();
        verificationTokenRepository.save(
                VerificationToken.create(user, tokenService.hash(rawToken), Instant.now().plus(VERIFICATION_TOKEN_TTL)));
        emailService.sendVerificationEmail(user.getEmail(), rawToken);
    }

    /**
     * Authenticates a user and issues an access token and a refresh token.
     *
     * @throws InvalidCredentialsException if the email is unknown or the password is wrong
     * @throws UserDeactivatedException if the account is deactivated
     * @throws EmailNotVerifiedException if the email has not been verified
     */
    @Transactional
    public LoginResponseDto login(LoginRequestDto loginRequestDto, String ipAddress) {
        Optional<User> user = userService.getByEmail(loginRequestDto.email());

        if(user.isPresent() && passwordEncoder.matches(loginRequestDto.password(), user.get().getPasswordHash())) {
            if(user.get().getStatus() == Status.DEACTIVATED) {
                auditEventService.loginFailure(user.get().getId(), loginRequestDto.email(), "account deactivated", ipAddress);
                throw new UserDeactivatedException("User account is not active");
            }

            if(!user.get().isEmailVerified()) {
                auditEventService.loginFailure(user.get().getId(), loginRequestDto.email(), "email not verified", ipAddress);
                throw new EmailNotVerifiedException("Email is not verified");
            }

            auditEventService.record(AuditEventType.LOGIN_SUCCESS, user.get().getId(), AuditTargetType.USER, user.get().getId(), "User logged in", ipAddress);
        } else {
            Long userId = user.map(User::getId).orElse(null);
            String reason = user.isPresent() ? "wrong password" : "unknown email";
            auditEventService.loginFailure(userId, loginRequestDto.email(), reason, ipAddress);
            throw new InvalidCredentialsException("Invalid credentials");
        }

        return issueTokens(user.get());
    }

    /**
     * Rotates a refresh token: revokes the presented token and issues a new token pair.
     * Presenting an already revoked token is treated as reuse and revokes all of the user's sessions.
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired, revoked or belongs to a deactivated user
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResponseDto refresh(String rawToken, String ipAddress) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        User user = refreshToken.getUser();

        if(refreshToken.isRevoked()) {
            refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId())
                    .forEach(RefreshToken::revoke);
            auditEventService.record(AuditEventType.REFRESH_TOKEN_REUSE_DETECTED, user.getId(), AuditTargetType.USER, user.getId(), "Revoked refresh token was reused, all sessions revoked", ipAddress);
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        if(!refreshToken.isActive()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        if(user.getStatus() == Status.DEACTIVATED) {
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }

        refreshToken.revoke();
        return issueTokens(user);
    }

    /**
     * Revokes the given refresh token. Idempotent: unknown or already revoked tokens are ignored.
     */
    @Transactional
    public void logout(String rawToken, String ipAddress) {
        refreshTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .filter(RefreshToken::isActive)
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    Long userId = refreshToken.getUser().getId();
                    auditEventService.record(AuditEventType.LOGOUT, userId, AuditTargetType.USER, userId, "User logged out", ipAddress);
                });
    }

    /**
     * Sends a password-reset link if the email is registered.
     * Always completes silently so the response does not reveal whether the email exists.
     */
    @Transactional
    public void forgotPassword(String email, String ipAddress) {
        userService.getByEmail(email).ifPresent(user -> {
            passwordResetTokenRepository.findAllByUserIdAndUsedAtIsNull(user.getId())
                    .forEach(PasswordResetToken::markUsed);

            String rawToken = tokenService.generateToken();
            passwordResetTokenRepository.save(
                    PasswordResetToken.create(user, tokenService.hash(rawToken), Instant.now().plus(PASSWORD_RESET_TOKEN_TTL)));
            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

            auditEventService.record(AuditEventType.PASSWORD_RESET_REQUESTED, user.getId(), AuditTargetType.USER, user.getId(), "Password reset requested", ipAddress);
        });
    }

    /**
     * Sets a new password using a single-use reset token and revokes all refresh tokens of the user.
     *
     * @throws InvalidTokenException if the token is unknown, expired or already used
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword, String ipAddress) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .filter(PasswordResetToken::isValid)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired password reset token"));

        token.markUsed();
        userService.changePassword(token.getUser().getId(), newPassword, ipAddress);
    }

    private String issueRefreshToken(User user) {
        String rawToken = tokenService.generateToken();
        refreshTokenRepository.save(RefreshToken.create(user, tokenService.hash(rawToken), Instant.now().plus(REFRESH_TOKEN_TTL)));
        return rawToken;
    }

    private LoginResponseDto issueTokens(User user) {
        return new LoginResponseDto(jwtService.generateAccessToken(user), issueRefreshToken(user), "Bearer", jwtService.getAccessTokenTtlSeconds());
    }
}
