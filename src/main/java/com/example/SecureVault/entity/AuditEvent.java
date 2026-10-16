package com.example.SecureVault.entity;

import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.AuditTargetType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEventType eventType;

    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    private AuditTargetType targetType;

    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String ipAddress;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Creates an audit event timestamped now.
     */
    public static AuditEvent create(AuditEventType eventType, Long actorUserId, AuditTargetType targetType,
                                    Long targetId, String details, String ipAddress) {
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.eventType = eventType;
        auditEvent.actorUserId = actorUserId;
        auditEvent.targetType = targetType;
        auditEvent.targetId = targetId;
        auditEvent.details = details;
        auditEvent.ipAddress = ipAddress;
        auditEvent.createdAt = Instant.now();
        return auditEvent;
    }
}
