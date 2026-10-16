package com.example.SecureVault.service;

import com.example.SecureVault.persistence.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditEventServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditEventService auditEventService;

    @Test
    public void getAuditEvents_alwaysSortsNewestFirst() {
        when(auditEventRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        auditEventService.getAuditEvents(PageRequest.of(0, 20, Sort.by("eventType")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findAll(captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    public void getAuditEvents_pageSizeAboveMax_isCappedAt100() {
        when(auditEventRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        auditEventService.getAuditEvents(PageRequest.of(2, 500));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
    }
}
