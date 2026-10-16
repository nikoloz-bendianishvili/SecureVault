package com.example.SecureVault.service;

import com.example.SecureVault.dto.RegisterRequestDto;
import com.example.SecureVault.dto.UserResponseDto;
import com.example.SecureVault.entity.RefreshToken;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.AuditTargetType;
import com.example.SecureVault.enums.Roles;
import com.example.SecureVault.enums.Status;
import com.example.SecureVault.exception.AdminNotChangingThemselvesException;
import com.example.SecureVault.exception.EmailAlreadyExistsException;
import com.example.SecureVault.exception.UserNotFoundException;
import com.example.SecureVault.persistence.RefreshTokenRepository;
import com.example.SecureVault.persistence.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * User account management and administrative operations on users.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventService auditEventService;

    /**
     * Registers a new user with a BCrypt-hashed password.
     *
     * @throws EmailAlreadyExistsException if the email is already registered
     */
    @Transactional
    public User create(RegisterRequestDto registerRequestDto, String ipAddress) {
        if(userRepository.existsByEmail(registerRequestDto.email())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }

        User user = new User(registerRequestDto.fullName(), registerRequestDto.email(), passwordEncoder.encode(registerRequestDto.password()));
        userRepository.save(user);

        auditEventService.record(AuditEventType.USER_REGISTERED, user.getId(), AuditTargetType.USER, user.getId(), "User created", ipAddress);
        return user;
    }

    /**
     * Returns the user with the given id.
     *
     * @throws UserNotFoundException if no such user exists
     */
    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    /**
     * Looks up a user by exact email address.
     */
    @Transactional(readOnly = true)
    public Optional<User> getByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Changes a user's role. Admin only.
     *
     * @throws AdminNotChangingThemselvesException if an admin targets their own account
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void changeRole(Long adminId, Long userId, Roles role, String ipAddress) {
        if(adminId.equals(userId)) {
            throw new AdminNotChangingThemselvesException("Admin cannot change their own role");
        }
        User user = getById(userId);
        Roles oldRole = user.getRole();
        user.setRole(role);

        auditEventService.record(AuditEventType.ROLE_CHANGED, adminId, AuditTargetType.USER, user.getId(), oldRole + " -> " + role, ipAddress);
    }

    /**
     * Activates or deactivates a user. Deactivation revokes all of the user's refresh tokens. Admin only.
     *
     * @throws AdminNotChangingThemselvesException if an admin targets their own account
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void changeStatus(Long adminId, Long userId, Status status, String ipAddress) {
        if(adminId.equals(userId)) {
            throw new AdminNotChangingThemselvesException("Admin cannot change their own status");
        }
        User user = getById(userId);
        Status oldStatus = user.getStatus();
        user.setStatus(status);

        if(status == Status.DEACTIVATED) {
            revokeAllRefreshTokens(user.getId());
        }

        auditEventService.record(AuditEventType.STATUS_CHANGED, adminId, AuditTargetType.USER, user.getId(), oldStatus + " -> " + status, ipAddress);
    }

    /**
     * Marks the user's email as verified.
     */
    @Transactional
    public void changeEmailVerified(Long userId, String ipAddress) {
        User user = getById(userId);
        user.setEmailVerified(true);
        auditEventService.record(AuditEventType.EMAIL_VERIFIED, user.getId(), AuditTargetType.USER, user.getId(), "User email verified changed to true", ipAddress);
    }

    /**
     * Replaces the user's password hash and revokes all of the user's refresh tokens.
     */
    @Transactional
    public void changePassword(Long userId, String newPassword, String ipAddress) {
        User user = getById(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        revokeAllRefreshTokens(user.getId());
        auditEventService.record(AuditEventType.PASSWORD_CHANGED, user.getId(), AuditTargetType.USER, user.getId(), "User password changed", ipAddress);
    }

    /**
     * Returns a page of users without sensitive fields. Admin only.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(user -> new UserResponseDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        ));
    }

    private void revokeAllRefreshTokens(Long userId) {
        refreshTokenRepository.findAllByUserIdAndRevokedFalse(userId).forEach(RefreshToken::revoke);
    }
}