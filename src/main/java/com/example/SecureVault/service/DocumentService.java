package com.example.SecureVault.service;

import com.example.SecureVault.dto.DocumentResponseDto;
import com.example.SecureVault.entity.Document;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.AuditTargetType;
import com.example.SecureVault.exception.DocumentNotFoundException;
import com.example.SecureVault.exception.InvalidShareException;
import com.example.SecureVault.exception.ShareAlreadyExistsException;
import com.example.SecureVault.exception.ShareNotFoundException;
import com.example.SecureVault.persistence.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * Document CRUD and sharing. Access is decided by ownership and explicit shares, independent of the user's role.
 * Users without access receive 404 so the existence of a document is not disclosed.
 */
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserService userService;
    private final AuditEventService auditEventService;

    /**
     * Creates a document owned by the given user.
     */
    @Transactional
    public DocumentResponseDto create(Long ownerId, String name, String text) {
        User owner = userService.getById(ownerId);
        return toDto(documentRepository.save(new Document(owner, name, text)));
    }

    /**
     * Returns a page of documents owned by the given user.
     */
    @Transactional(readOnly = true)
    public Page<DocumentResponseDto> listOwn(Long ownerId, Pageable pageable) {
        return documentRepository.findByOwnerId(ownerId, pageable).map(this::toDto);
    }

    /**
     * Returns a document readable by the user (owner or shared user).
     *
     * @throws DocumentNotFoundException if the document does not exist or the user has no access
     */
    @Transactional(readOnly = true)
    public DocumentResponseDto get(Long userId, Long documentId) {
        return toDto(findReadable(userId, documentId));
    }

    /**
     * Updates the non-null fields of a document. Owner only.
     *
     * @throws DocumentNotFoundException if the document does not exist or the user has no access
     * @throws AccessDeniedException if the user can read but does not own the document
     */
    @Transactional
    public DocumentResponseDto update(Long userId, Long documentId, String name, String text) {
        Document document = findOwned(userId, documentId);
        if (name != null) {
            document.setName(name);
        }
        if (text != null) {
            document.setText(text);
        }
        return toDto(document);
    }

    /**
     * Deletes a document and its shares. Owner only.
     *
     * @throws DocumentNotFoundException if the document does not exist or the user has no access
     * @throws AccessDeniedException if the user can read but does not own the document
     */
    @Transactional
    public void delete(Long userId, Long documentId) {
        Document document = findOwned(userId, documentId);
        documentRepository.delete(document);
    }

    /**
     * Grants read access to another registered user. Owner only.
     *
     * @throws InvalidShareException if the owner shares with themselves
     * @throws UserNotFoundException if the recipient does not exist
     * @throws ShareAlreadyExistsException if the document is already shared with the recipient
     */
    @Transactional
    public void share(Long ownerId, Long documentId, Long targetUserId, String ipAddress) {
        Document document = findOwned(ownerId, documentId);

        if (ownerId.equals(targetUserId)) {
            throw new InvalidShareException("Cannot share a document with yourself");
        }

        User target = userService.getById(targetUserId);
        if (!document.getSharedWith().add(target)) {
            throw new ShareAlreadyExistsException("Document is already shared with this user");
        }

        auditEventService.record(AuditEventType.DOCUMENT_SHARED, ownerId, AuditTargetType.DOCUMENT, documentId, "Shared with userId=" + targetUserId, ipAddress);
    }

    /**
     * Revokes a previously granted read access. Owner only.
     *
     * @throws ShareNotFoundException if the document is not shared with the user
     */
    @Transactional
    public void revokeShare(Long ownerId, Long documentId, Long targetUserId, String ipAddress) {
        Document document = findOwned(ownerId, documentId);

        if (!document.getSharedWith().removeIf(user -> user.getId().equals(targetUserId))) {
            throw new ShareNotFoundException("Document is not shared with this user");
        }

        auditEventService.record(AuditEventType.DOCUMENT_ACCESS_REVOKED, ownerId, AuditTargetType.DOCUMENT, documentId, "Access revoked for userId=" + targetUserId, ipAddress);
    }

    private Document findReadable(Long userId, Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        boolean isOwner = isOwner(document, userId);
        boolean isShared = document.getSharedWith().stream().anyMatch(user -> user.getId().equals(userId));
        if (!isOwner && !isShared) {
            throw new DocumentNotFoundException("Document not found");
        }
        return document;
    }

    private Document findOwned(Long userId, Long documentId) {
        Document document = findReadable(userId, documentId);
        if (!isOwner(document, userId)) {
            throw new AccessDeniedException("Only the owner can modify this document");
        }
        return document;
    }

    private boolean isOwner(Document document, Long userId) {
        return document.getOwner().getId().equals(userId);
    }

    private DocumentResponseDto toDto(Document document) {
        return new DocumentResponseDto(
                document.getId(),
                document.getOwner().getId(),
                document.getName(),
                document.getText(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getSharedWith().stream().map(User::getId).collect(Collectors.toSet())
        );
    }
}