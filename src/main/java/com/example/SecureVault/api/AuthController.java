package com.example.SecureVault.api;

import com.example.SecureVault.dto.*;
import com.example.SecureVault.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a user and sends a verification email. Returns 201.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDto request,
                                                     HttpServletRequest httpRequest) {
        authService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(201).build();
    }

    /**
     * Verifies an email address with the token from the verification email.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequestDto request, HttpServletRequest httpServletRequest) {
        authService.verifyEmail(request.token(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    /**
     * Resends the verification email. Always returns 200.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequestDto request,
                                                   HttpServletRequest httpServletRequest) {
        authService.resendVerification(request.email(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    /**
     * Exchanges credentials for an access token and a refresh token.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto,
                                                  HttpServletRequest httpServletRequest) {
        LoginResponseDto response = authService.login(loginRequestDto, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    /**
     * Revokes a refresh token. Returns 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequestDto request, HttpServletRequest httpServletRequest) {
        authService.logout(request.token(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * Requests a password-reset email. Always returns 200.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request, HttpServletRequest httpServletRequest) {
        authService.forgotPassword(request.email(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    /**
     * Sets a new password with the token from the reset email.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDto request, HttpServletRequest httpServletRequest) {
        authService.resetPassword(request.token(), request.newPassword(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    /**
     * Rotates a refresh token and returns a new token pair.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto refreshTokenRequestDto,
                                        HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(authService.refresh(refreshTokenRequestDto.refreshToken(), httpServletRequest.getRemoteAddr()));
    }
}
