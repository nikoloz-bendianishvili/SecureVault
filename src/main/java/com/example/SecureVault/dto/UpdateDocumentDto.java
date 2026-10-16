package com.example.SecureVault.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDocumentDto(
        @Size(max = 255) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        String text
) {}
