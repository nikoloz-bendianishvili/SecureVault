package com.example.SecureVault.dto;

import java.time.Instant;
import java.util.Set;

public record DocumentResponseDto(
        Long id,
        Long ownerId,
        String name,
        String text,
        Instant createdAt,
        Instant updatedAt,
        Set<Long> sharedWithUserIds
) {}
