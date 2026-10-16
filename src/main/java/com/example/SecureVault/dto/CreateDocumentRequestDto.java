package com.example.SecureVault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequestDto(
        @NotBlank @Size(max = 255) String name,
        @NotNull String text
) {}
