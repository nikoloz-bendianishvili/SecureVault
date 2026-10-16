package com.example.SecureVault.api;

import com.example.SecureVault.dto.CreateDocumentRequestDto;
import com.example.SecureVault.dto.DocumentResponseDto;
import com.example.SecureVault.dto.ShareDocumentRequestDto;
import com.example.SecureVault.dto.UpdateDocumentDto;
import com.example.SecureVault.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Document and sharing endpoints for authenticated users.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Creates a document owned by the caller. Returns 201.
     */
    @PostMapping
    public ResponseEntity<DocumentResponseDto> createDocument(@AuthenticationPrincipal Long userId,
                                                              @Valid @RequestBody CreateDocumentRequestDto request) {
        DocumentResponseDto document = documentService.create(userId, request.name(), request.text());
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    /**
     * Lists the caller's own documents, newest first by default.
     */
    @GetMapping
    public ResponseEntity<Page<DocumentResponseDto>> getDocuments(@AuthenticationPrincipal Long userId,
                                                                  @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(documentService.listOwn(userId, pageable));
    }

    /**
     * Returns a document the caller owns or has been granted access to.
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponseDto> getDocument(@AuthenticationPrincipal Long userId,
                                                           @PathVariable Long documentId) {
        return ResponseEntity.ok(documentService.get(userId, documentId));
    }

    /**
     * Partially updates a document. Owner only.
     */
    @PatchMapping("/{documentId}")
    public ResponseEntity<DocumentResponseDto> updateDocument(@AuthenticationPrincipal Long userId,
                                                              @PathVariable Long documentId,
                                                              @Valid @RequestBody UpdateDocumentDto request) {
        return ResponseEntity.ok(documentService.update(userId, documentId, request.name(), request.text()));
    }

    /**
     * Deletes a document. Owner only. Returns 204.
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long documentId) {
        documentService.delete(userId, documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Grants read access to another user. Owner only. Returns 204.
     */
    @PostMapping("/{documentId}/shares")
    public ResponseEntity<Void> shareDocument(@AuthenticationPrincipal Long userId,
                                              @PathVariable Long documentId,
                                              @Valid @RequestBody ShareDocumentRequestDto request,
                                              HttpServletRequest httpServletRequest) {
        documentService.share(userId, documentId, request.userId(), httpServletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /**
     * Revokes a user's read access. Owner only. Returns 204.
     */
    @DeleteMapping("/{documentId}/shares/{targetUserId}")
    public ResponseEntity<Void> unshareDocument(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long documentId,
                                                @PathVariable Long targetUserId,
                                                HttpServletRequest httpServletRequest) {
        documentService.revokeShare(userId, documentId, targetUserId, httpServletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
