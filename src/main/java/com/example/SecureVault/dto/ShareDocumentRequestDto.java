package com.example.SecureVault.dto;

import jakarta.validation.constraints.NotNull;

public record ShareDocumentRequestDto(
        @NotNull Long userId
) {}
