package com.example.SecureVault.dto;

import com.example.SecureVault.enums.Roles;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequestDto(
        @NotNull Roles role
) {}
