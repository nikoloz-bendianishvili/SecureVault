package com.example.SecureVault.dto;

import com.example.SecureVault.enums.Roles;
import com.example.SecureVault.enums.Status;

import java.time.Instant;

public record UserResponseDto(
        Long id,
        String fullName,
        String email,
        Roles role,
        Status status,
        boolean emailVerified,
        Instant createdAt,
        Instant updatedAt
) {}
