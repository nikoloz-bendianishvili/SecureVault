package com.example.SecureVault.dto;

import com.example.SecureVault.entity.AuditEvent;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.AuditTargetType;

import java.time.Instant;

public record AuditEventResponseDto(
        Long id,
        AuditEventType eventType,
        Long actorUserId,
        AuditTargetType targetType,
        Long targetId,
        String details,
        String ipAddress,
        Instant createdAt
) {}
