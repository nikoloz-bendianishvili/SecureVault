package com.example.SecureVault.persistence;

import com.example.SecureVault.TestcontainersConfiguration;
import com.example.SecureVault.entity.AuditEvent;
import com.example.SecureVault.enums.AuditEventType;
import com.example.SecureVault.enums.AuditTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public class AuditEventRepositoryTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    public void save_eventWithoutActorAndTarget_persists() {
        AuditEvent saved = auditEventRepository.saveAndFlush(
                AuditEvent.create(AuditEventType.LOGIN_FAILURE, null, null, null, "email=unknown@gmail.com, unknown email", "127.0.0.1"));
        entityManager.clear();

        AuditEvent found = auditEventRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILURE);
        assertThat(found.getActorUserId()).isNull();
        assertThat(found.getTargetType()).isNull();
        assertThat(found.getTargetId()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    public void findAll_sortedByCreatedAtAndIdDesc_returnsNewestFirst() {
        AuditEvent first = auditEventRepository.save(AuditEvent.create(AuditEventType.USER_REGISTERED, 1L, AuditTargetType.USER, 1L, "first", null));
        AuditEvent second = auditEventRepository.save(AuditEvent.create(AuditEventType.EMAIL_VERIFIED, 1L, AuditTargetType.USER, 1L, "second", null));
        AuditEvent third = auditEventRepository.save(AuditEvent.create(AuditEventType.LOGIN_SUCCESS, 1L, AuditTargetType.USER, 1L, "third", null));

        Page<AuditEvent> page = auditEventRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(page.getContent()).extracting(AuditEvent::getId)
                .containsExactly(third.getId(), second.getId(), first.getId());
    }
}
