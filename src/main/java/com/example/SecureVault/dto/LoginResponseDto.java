package com.example.SecureVault.dto;

public record LoginResponseDto(
   String accessToken,
   String refreshToken,
   String tokenType,
   Long expiresIn
) {}
