package com.kemaless.core.service.impl;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.dto.request.CreateRealmRequest;
import com.kemaless.core.dto.request.UpdateRealmRequest;
import com.kemaless.core.dto.response.*;
import com.kemaless.core.exception.error.RealmAlreadyExistException;
import com.kemaless.core.exception.error.RealmNotFoundException;
import com.kemaless.core.service.interfaces.AuthModuleService;
import com.kemaless.core.service.interfaces.RealmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import com.kemaless.core.model.Realm;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.repository.RealmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealmServiceImpl implements RealmService {

    private final RealmRepository realmRepository;
    private final AuthModuleService authModuleService;
    private final AuditService auditService;

    @Override
    @Transactional
    public CreateRealmResponse create(CreateRealmRequest request) {
        log.info("Creating new realm with code: {}", request.code());

        if (realmRepository.existsByCode(request.code())) {
            log.warn("Failed to create realm: code '{}' already exists", request.code());
            throw new RealmAlreadyExistException(request.code());
        }

        Realm realm = Realm.builder()
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .status(RealmStatus.ACTIVE)
                .build();

        realm = realmRepository.save(realm);
        log.debug("Realm saved to database with ID: {}", realm.getId());

        var credentials = authModuleService.createDefaultClient(realm.getCode());
        log.debug("Default client created for realm: {}", realm.getCode());

        auditService.write(realm.getCode(), AuditAction.REALM_CREATED.name(), "REALM", realm.getCode());

        log.info("Successfully created realm: {}", realm.getCode());
        return CreateRealmResponse.builder()
                .code(realm.getCode())
                .name(realm.getName())
                .description(realm.getDescription())
                .status(realm.getStatus())
                .clientId(credentials.clientId())
                .clientSecret(credentials.clientSecret())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RealmDetailsResponse getByCode(String realmCode) {
        log.debug("Fetching realm details for code: {}", realmCode);

        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> {
                    log.warn("Realm not found: {}", realmCode);
                    return new RealmNotFoundException(realmCode);
                });

        return RealmDetailsResponse.builder()
                .code(realm.getCode())
                .name(realm.getName())
                .description(realm.getDescription())
                .status(realm.getStatus())
                .miniAppsCount(realmRepository.countConnectedMiniApps(realmCode))
                .clientsCount(realmRepository.countActiveClients(realmCode))
                .rolesCount(realmRepository.countRealmRoles(realmCode))
                .createdAt(realm.getCreatedAt())
                .updatedAt(realm.getUpdatedAt())
                .build();
    }

    @Override
    public PageResponse<RealmListItemResponse> findAll(String search, String status, Pageable pageable) {
        log.debug("Fetching realms list. Search: '{}', Status: '{}', Pageable: {}", search, status, pageable);

        String searchPattern = null;
        if (StringUtils.hasText(search)) {
            searchPattern = "%" + search.trim() + "%";
        }
        RealmStatus statusParam = null;
        if (StringUtils.hasText(status)) {
            try {
                statusParam = RealmStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid realm status filter: \"%s\". Allowed: %s".formatted(status.trim(), Arrays.toString(RealmStatus.values())),
                        e);
            }
        }

        Page<RealmListItemResponse> page = realmRepository.findAllWithCounts(searchPattern, statusParam, pageable);

        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Override
    @Transactional
    public void update(String realmCode, UpdateRealmRequest request) {
        log.info("Updating realm with code: {}", realmCode);

        Realm realm = realmRepository.findByCode(realmCode)
                .filter(r -> r.getStatus() != RealmStatus.DELETED)
                .orElseThrow(() -> {
                    log.warn("Realm not found or deleted for update: {}", realmCode);
                    return new RealmNotFoundException(realmCode);
                });

        if (StringUtils.hasText(request.name())) {
            realm.setName(request.name());
        }
        if (StringUtils.hasText(request.description())) {
            realm.setDescription(request.description());
        }

        realmRepository.save(realm);
        auditService.write(realm.getCode(), AuditAction.REALM_UPDATED.name(), "REALM", realm.getCode());

        log.info("Successfully updated realm: {}", realmCode);
    }

    @Override
    @Transactional
    public void changeStatus(String realmCode, boolean enable) {
        log.info("Changing status for realm: {} to enable: {}", realmCode, enable);

        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> {
                    log.warn("Realm not found for status change: {}", realmCode);
                    return new RealmNotFoundException(realmCode);
                });

        realm.setStatus(enable ? RealmStatus.ACTIVE : RealmStatus.DISABLED);
        realmRepository.save(realm);
        AuditAction action = enable ? AuditAction.REALM_ENABLED : AuditAction.REALM_DISABLED;
        auditService.write(realm.getCode(), action.name(), "REALM", realm.getCode());

        log.info("Successfully changed status for realm: {} to {}", realmCode, realm.getStatus());
    }

    @Override
    @Transactional
    public void delete(String realmCode) {
        log.info("Attempting to soft delete realm: {}", realmCode);

        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> {
                    log.warn("Realm not found for deletion: {}", realmCode);
                    return new RealmNotFoundException(realmCode);
                });

        realm.setStatus(RealmStatus.DELETED);
        authModuleService.disableAllClients(realm.getCode());
        realmRepository.save(realm);
        auditService.write(realm.getCode(), AuditAction.REALM_DELETED.name(), "REALM", realm.getCode());

        log.info("Successfully soft deleted realm: {}", realmCode);
    }

}
