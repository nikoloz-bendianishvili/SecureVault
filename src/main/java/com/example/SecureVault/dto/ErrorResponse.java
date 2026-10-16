package com.example.SecureVault.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Map<String, String> errors,
        Instant timestamp
) {}
