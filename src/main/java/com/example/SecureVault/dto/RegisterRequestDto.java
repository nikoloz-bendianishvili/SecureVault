package com.example.SecureVault.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.NonNull;

public record RegisterRequestDto(
    @NotBlank @Size(min = 2, max = 100) String fullName,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, max = 100) String password
) {}
