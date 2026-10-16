package com.example.SecureVault.persistence;

import com.example.SecureVault.TestcontainersConfiguration;
import com.example.SecureVault.entity.Document;
import com.example.SecureVault.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User createUser(String email) {
        return userRepository.save(new User("fullName", email, "passwordHash"));
    }

    @Test
    public void findByOwnerId_returnsOnlyOwnersDocuments() {
        User owner = createUser("owner@gmail.com");
        User other = createUser("other@gmail.com");
        documentRepository.save(new Document(owner, "first", "text"));
        documentRepository.save(new Document(owner, "second", "text"));
        documentRepository.save(new Document(other, "foreign", "text"));

        Page<Document> page = documentRepository.findByOwnerId(owner.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Document::getName).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    public void findByOwnerId_noDocuments_returnsEmptyPage() {
        User owner = createUser("owner@gmail.com");

        Page<Document> page = documentRepository.findByOwnerId(owner.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    public void findByOwnerId_pageSmallerThanResults_returnsRequestedPage() {
        User owner = createUser("owner@gmail.com");
        documentRepository.save(new Document(owner, "first", "text"));
        documentRepository.save(new Document(owner, "second", "text"));
        documentRepository.save(new Document(owner, "third", "text"));

        Page<Document> page = documentRepository.findByOwnerId(owner.getId(), PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    public void save_documentSharedWithUser_persistsShare() {
        User owner = createUser("owner@gmail.com");
        User reader = createUser("reader@gmail.com");
        Document document = new Document(owner, "shared", "text");
        document.getSharedWith().add(reader);
        documentRepository.saveAndFlush(document);
        entityManager.clear();

        Document found = documentRepository.findById(document.getId()).orElseThrow();

        assertThat(found.getSharedWith()).extracting(User::getId).containsExactly(reader.getId());
    }

    @Test
    public void save_shareRemoved_deletesShare() {
        User owner = createUser("owner@gmail.com");
        User reader = createUser("reader@gmail.com");
        Document document = new Document(owner, "shared", "text");
        document.getSharedWith().add(reader);
        documentRepository.saveAndFlush(document);
        entityManager.clear();

        Document loaded = documentRepository.findById(document.getId()).orElseThrow();
        loaded.getSharedWith().removeIf(user -> user.getId().equals(reader.getId()));
        documentRepository.flush();
        entityManager.clear();

        Document found = documentRepository.findById(document.getId()).orElseThrow();

        assertThat(found.getSharedWith()).isEmpty();
    }

    @Test
    public void delete_sharedDocument_deletesDocumentAndShares() {
        User owner = createUser("owner@gmail.com");
        User reader = createUser("reader@gmail.com");
        Document document = new Document(owner, "shared", "text");
        document.getSharedWith().add(reader);
        documentRepository.saveAndFlush(document);

        documentRepository.delete(document);
        documentRepository.flush();
        entityManager.clear();

        assertThat(documentRepository.findById(document.getId())).isEmpty();
        assertThat(userRepository.findById(reader.getId())).isPresent();
    }
}
