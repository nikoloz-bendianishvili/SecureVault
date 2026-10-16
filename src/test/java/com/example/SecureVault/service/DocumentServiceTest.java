package com.example.SecureVault.service;

import com.example.SecureVault.dto.DocumentResponseDto;
import com.example.SecureVault.entity.Document;
import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.exception.DocumentNotFoundException;
import com.example.SecureVault.exception.InvalidShareException;
import com.example.SecureVault.exception.ShareAlreadyExistsException;
import com.example.SecureVault.exception.ShareNotFoundException;
import com.example.SecureVault.exception.UserNotFoundException;
import com.example.SecureVault.persistence.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditEventService auditEventService;

    @InjectMocks
    private DocumentService documentService;

    private User user(Long id) {
        User user = new User("fullName", "user" + id + "@gmail.com", "passwordHash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Document document(User owner) {
        Document document = new Document(owner, "name", "text");
        ReflectionTestUtils.setField(document, "id", 10L);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        return document;
    }

    @Test
    public void create_savesDocumentForOwner() {
        User owner = user(1L);
        when(userService.getById(1L)).thenReturn(owner);
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentResponseDto result = documentService.create(1L, "name", "text");

        assertThat(result.ownerId()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("name");
    }

    @Test
    public void get_owner_returnsDocument() {
        document(user(1L));

        assertThat(documentService.get(1L, 10L).id()).isEqualTo(10L);
    }

    @Test
    public void get_sharedUser_returnsDocument() {
        Document document = document(user(1L));
        document.getSharedWith().add(user(2L));

        assertThat(documentService.get(2L, 10L).id()).isEqualTo(10L);
    }

    @Test
    public void get_userWithoutAccess_throwsNotFound() {
        document(user(1L));

        assertThatThrownBy(() -> documentService.get(3L, 10L)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    public void get_unknownDocument_throwsNotFound() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.get(1L, 99L)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    public void update_owner_changesOnlyGivenFields() {
        document(user(1L));

        DocumentResponseDto result = documentService.update(1L, 10L, null, "new text");

        assertThat(result.name()).isEqualTo("name");
        assertThat(result.text()).isEqualTo("new text");
    }

    @Test
    public void update_sharedUser_throwsAccessDenied() {
        Document document = document(user(1L));
        document.getSharedWith().add(user(2L));

        assertThatThrownBy(() -> documentService.update(2L, 10L, "name", "text")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    public void delete_sharedUser_throwsAccessDenied() {
        Document document = document(user(1L));
        document.getSharedWith().add(user(2L));

        assertThatThrownBy(() -> documentService.delete(2L, 10L)).isInstanceOf(AccessDeniedException.class);
        verify(documentRepository, never()).delete(any());
    }

    @Test
    public void delete_userWithoutAccess_throwsNotFound() {
        document(user(1L));

        assertThatThrownBy(() -> documentService.delete(3L, 10L)).isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    public void share_validUser_addsShareAndAudits() {
        Document document = document(user(1L));
        User reader = user(2L);
        when(userService.getById(2L)).thenReturn(reader);

        documentService.share(1L, 10L, 2L, "ip");

        assertThat(document.getSharedWith()).contains(reader);
        verify(auditEventService).record(eq(AuditEventType.DOCUMENT_SHARED), eq(1L), any(), eq(10L), anyString(), eq("ip"));
    }

    @Test
    public void share_withYourself_throwsInvalidShare() {
        document(user(1L));

        assertThatThrownBy(() -> documentService.share(1L, 10L, 1L, "ip")).isInstanceOf(InvalidShareException.class);
    }

    @Test
    public void share_alreadyShared_throwsShareAlreadyExists() {
        Document document = document(user(1L));
        User reader = user(2L);
        document.getSharedWith().add(reader);
        when(userService.getById(2L)).thenReturn(reader);

        assertThatThrownBy(() -> documentService.share(1L, 10L, 2L, "ip")).isInstanceOf(ShareAlreadyExistsException.class);
    }

    @Test
    public void share_unknownRecipient_throwsUserNotFound() {
        document(user(1L));
        when(userService.getById(99L)).thenThrow(new UserNotFoundException("User not found"));

        assertThatThrownBy(() -> documentService.share(1L, 10L, 99L, "ip")).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    public void share_bySharedUser_throwsAccessDenied() {
        Document document = document(user(1L));
        document.getSharedWith().add(user(2L));

        assertThatThrownBy(() -> documentService.share(2L, 10L, 3L, "ip")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    public void revokeShare_sharedUser_removesShareAndAudits() {
        Document document = document(user(1L));
        document.getSharedWith().add(user(2L));

        documentService.revokeShare(1L, 10L, 2L, "ip");

        assertThat(document.getSharedWith()).isEmpty();
        verify(auditEventService).record(eq(AuditEventType.DOCUMENT_ACCESS_REVOKED), eq(1L), any(), eq(10L), anyString(), eq("ip"));
    }

    @Test
    public void revokeShare_userNotShared_throwsShareNotFound() {
        document(user(1L));

        assertThatThrownBy(() -> documentService.revokeShare(1L, 10L, 2L, "ip")).isInstanceOf(ShareNotFoundException.class);
    }
}
