package com.example.SecureVault.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequestDto(
        @NotBlank String token
) {}
