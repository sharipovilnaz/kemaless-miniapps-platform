package com.kemaless.core.service.impl;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.dto.request.CreateRoleMappingRequest;
import com.kemaless.core.dto.request.UpdateRoleMappingRequest;
import com.kemaless.core.dto.response.ObservedRoleResponse;
import com.kemaless.core.dto.response.RoleMappingResponse;
import com.kemaless.core.enums.ObservedRoleStatus;
import com.kemaless.core.exception.error.*;
import com.kemaless.core.model.ExternalRoleMapping;
import com.kemaless.core.model.ObservedRole;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmRole;
import com.kemaless.core.repository.ExternalRoleMappingRepository;
import com.kemaless.core.repository.ObservedRoleRepository;
import com.kemaless.core.repository.RealmRepository;
import com.kemaless.core.repository.RealmRoleRepository;
import com.kemaless.core.service.interfaces.ExternalRoleMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalRoleMappingServiceImpl implements ExternalRoleMappingService {

    private final ExternalRoleMappingRepository mappingRepository;
    private final ObservedRoleRepository observedRoleRepository;
    private final RealmRepository realmRepository;
    private final RealmRoleRepository realmRoleRepository;
    private final AuditService auditService;

    @Transactional
    public RoleMappingResponse createMapping(String realmCode, CreateRoleMappingRequest request) {
        log.info("Creating mapping for external role '{}' to realm role '{}' in realm '{}'",
                request.externalRole(), request.realmRoleCode(), realmCode);

        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> new RealmNotFoundException(realmCode));

        RealmRole realmRole = realmRoleRepository.findByRealmCodeAndCode(realmCode, request.realmRoleCode())
                .orElseThrow(() -> new RealmRoleNotFoundException(realmCode, request.realmRoleCode()));

        if (mappingRepository.existsByRealmCodeAndExternalRole(realmCode, request.externalRole())) {
            throw new RoleMappingAlreadyExistException(realmCode, request.externalRole());
        }

        ExternalRoleMapping mapping = ExternalRoleMapping.builder()
                .realm(realm)
                .externalRole(request.externalRole())
                .realmRole(realmRole)
                .source(request.source())
                .build();

        mappingRepository.save(mapping);
        observedRoleRepository.findByRealmCodeAndExternalRoleAndSource(realmCode, request.externalRole(), request.source())
                .ifPresent(observed -> {
                    observed.setStatus(ObservedRoleStatus.MAPPED);
                    observedRoleRepository.save(observed);
                    log.debug("Updated observed role '{}' status to MAPPED", request.externalRole());
                });
        auditService.write(realmCode, AuditAction.ROLE_MAPPING_CREATED.name(), "ROLE_MAPPING", mapping.getId().toString());

        return mapToMappingDto(mapping);
    }

    @Transactional(readOnly = true)
    public List<RoleMappingResponse> getMappings(String realmCode) {
        return mappingRepository.findAllByRealmCode(realmCode).stream()
                .map(this::mapToMappingDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleMappingResponse updateMapping(String realmCode, UUID mappingId, UpdateRoleMappingRequest request) {
        log.info("Updating mapping {} in realm {}", mappingId, realmCode);

        ExternalRoleMapping mapping = getMappingOrThrow(realmCode, mappingId);

        RealmRole newRealmRole = realmRoleRepository.findByRealmCodeAndCode(realmCode, request.realmRoleCode())
                .orElseThrow(() -> new RealmRoleNotFoundException(realmCode, request.realmRoleCode()));

        mapping.setRealmRole(newRealmRole);
        mappingRepository.save(mapping);
        auditService.write(realmCode, AuditAction.ROLE_MAPPING_UPDATED.name(), "ROLE_MAPPING", mapping.getId().toString());

        return mapToMappingDto(mapping);
    }

    @Transactional
    public void deleteMapping(String realmCode, UUID mappingId) {
        log.info("Deleting mapping {} in realm {}", mappingId, realmCode);

        ExternalRoleMapping mapping = getMappingOrThrow(realmCode, mappingId);

        mappingRepository.delete(mapping);
        auditService.write(realmCode, AuditAction.ROLE_MAPPING_DELETED.name(), "ROLE_MAPPING", mappingId.toString());
    }

    @Transactional(readOnly = true)
    public List<ObservedRoleResponse> getObservedRoles(String realmCode) {
        return observedRoleRepository.findAllByRealmCode(realmCode).stream()
                .map(this::mapToObservedDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void ignoreObservedRole(String realmCode, UUID observedId) {
        log.info("Ignoring observed role {} in realm {}", observedId, realmCode);

        ObservedRole observed = observedRoleRepository.findByRealmCodeAndId(realmCode, observedId)
                .orElseThrow(() -> new ObservedRoleNotFoundException(observedId));

        observed.setStatus(ObservedRoleStatus.IGNORED);
        observedRoleRepository.save(observed);

    }

    private ExternalRoleMapping getMappingOrThrow(String realmCode, UUID mappingId) {
        return mappingRepository.findByRealmCodeAndId(realmCode, mappingId)
                .orElseThrow(() -> new RoleMappingNotFoundException(mappingId));
    }

    private RoleMappingResponse mapToMappingDto(ExternalRoleMapping mapping) {
        return RoleMappingResponse.builder()
                .id(mapping.getId())
                .externalRole(mapping.getExternalRole())
                .realmRoleCode(mapping.getRealmRole().getCode())
                .source(mapping.getSource())
                .createdAt(mapping.getCreatedAt())
                .build();
    }

    private ObservedRoleResponse mapToObservedDto(ObservedRole role) {
        return ObservedRoleResponse.builder()
                .id(role.getId())
                .externalRole(role.getExternalRole())
                .source(role.getSource())
                .status(role.getStatus())
                .firstSeenAt(role.getFirstSeenAt())
                .lastSeenAt(role.getLastSeenAt())
                .seenCount(role.getSeenCount())
                .build();
    }
}
