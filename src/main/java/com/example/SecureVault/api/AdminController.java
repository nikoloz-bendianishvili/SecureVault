package com.example.SecureVault.api;

import com.example.SecureVault.dto.AuditEventResponseDto;
import com.example.SecureVault.dto.ChangeRoleRequestDto;
import com.example.SecureVault.dto.ChangeStatusRequestDto;
import com.example.SecureVault.dto.UserResponseDto;
import com.example.SecureVault.service.AuditEventService;
import com.example.SecureVault.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Administrative endpoints. Restricted to ADMIN at both URL and method level.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AuditEventService auditEventService;

    /**
     * Lists users.
     */
    @GetMapping("/users")
    public ResponseEntity<Page<UserResponseDto>> getAllUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /**
     * Changes a user's role. Returns 204.
     */
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<Void> updateUserRole(@AuthenticationPrincipal Long adminId,
                                               @PathVariable Long userId, @Valid @RequestBody ChangeRoleRequestDto changeRoleRequestDto,
                                               HttpServletRequest httpServletRequest) {
        userService.changeRole(adminId, userId, changeRoleRequestDto.role(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * Activates or deactivates a user. Returns 204.
     */
    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(@AuthenticationPrincipal Long adminId,
                                                 @PathVariable Long userId, @Valid @RequestBody ChangeStatusRequestDto changeStatusRequestDto,
                                                 HttpServletRequest httpServletRequest) {
        userService.changeStatus(adminId, userId, changeStatusRequestDto.status(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists audit events, newest first.
     */
    @GetMapping("/audit-events")
    public ResponseEntity<Page<AuditEventResponseDto>> getAuditEvents(Pageable pageable) {
        return ResponseEntity.ok(auditEventService.getAuditEvents(pageable));
    }
}
