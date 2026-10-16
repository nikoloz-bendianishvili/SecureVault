package com.example.SecureVault.dto;

import com.example.SecureVault.enums.Status;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequestDto(
        @NotNull Status status
) {}
