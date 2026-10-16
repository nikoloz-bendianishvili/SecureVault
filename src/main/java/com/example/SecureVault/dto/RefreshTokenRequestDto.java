package com.example.SecureVault.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
        @NotBlank String refreshToken
) {}
