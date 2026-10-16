package com.example.SecureVault.service;

import com.example.SecureVault.dto.AuditEventResponseDto;
import com.example.SecureVault.entity.AuditEvent;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.AuditTargetType;
import com.example.SecureVault.persistence.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and queries security audit events. Details must never contain passwords or raw tokens.
 */
@Service
@RequiredArgsConstructor
public class AuditEventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository auditEventRepository;

    /**
     * Persists an audit event in the caller's transaction.
     */
    public void record(AuditEventType eventType, Long actorUserId, AuditTargetType targetType, Long targetId, String details, String ipAddress) {
        AuditEvent auditEvent = AuditEvent.create(eventType, actorUserId, targetType, targetId, details, ipAddress);
        auditEventRepository.save(auditEvent);
    }

    /**
     * Records a failed login in a separate transaction so it survives the caller's rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loginFailure(Long userId, String email, String reason, String ipAddress) {
        AuditTargetType targetType = userId != null ? AuditTargetType.USER : null;
        record(AuditEventType.LOGIN_FAILURE, userId, targetType, userId, "email=" + email + ", " + reason, ipAddress);
    }

    /**
     * Records a failed email verification in a separate transaction so it survives the caller's rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void emailVerificationFailure(Long userId, String reason, String ipAddress) {
        AuditTargetType targetType = userId != null ? AuditTargetType.USER : null;
        record(AuditEventType.EMAIL_VERIFICATION_FAILED, userId, targetType, userId, reason, ipAddress);
    }

    /**
     * Returns audit events newest first (createdAt, then id), with the page size capped at 100. Admin only.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<AuditEventResponseDto> getAuditEvents(Pageable pageable) {
        Pageable newestFirst = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        return auditEventRepository.findAll(newestFirst).map(auditEvent -> new AuditEventResponseDto(
                auditEvent.getId(),
                auditEvent.getEventType(),
                auditEvent.getActorUserId(),
                auditEvent.getTargetType(),
                auditEvent.getTargetId(),
                auditEvent.getDetails(),
                auditEvent.getIpAddress(),
                auditEvent.getCreatedAt()
        ));
    }

}
