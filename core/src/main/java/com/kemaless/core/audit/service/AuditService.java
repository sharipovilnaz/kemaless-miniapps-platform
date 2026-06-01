package com.kemaless.core.audit.service;

import com.kemaless.core.audit.domain.AuditEventEntity;
import com.kemaless.core.audit.domain.event.AuditEvent;
import com.kemaless.core.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAuditEvent(AuditEvent event) {
        log.debug("Processing audit event async: {}", event.action());

        write(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent event) {
        AuditEventEntity entity = AuditEventEntity.builder()
                .timestamp(Instant.now())
                .actorType(event.actorType())
                .actorId(event.actorId())
                .realmCode(event.realmCode())
                .action(event.action())
                .resourceType(event.resourceType())
                .resourceId(event.resourceId())
                .status(event.status())
                .ip(event.ip())
                .userAgent(event.userAgent())
                .metadata(event.metadata())
                .build();
        try {
            auditEventRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to save audit event to database", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String realmCode, String action, String resourceType, String resourceId) {
        write(AuditEvent.builder()
                .actorType("SYSTEM")
                .actorId("platform")
                .realmCode(realmCode)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .status("SUCCESS")
                .build());
    }
}

