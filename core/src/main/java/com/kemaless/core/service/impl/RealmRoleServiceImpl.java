package com.kemaless.core.service.impl;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.dto.request.CreateRealmRoleRequest;
import com.kemaless.core.dto.request.UpdateRealmRoleRequest;
import com.kemaless.core.dto.response.RealmRoleResponse;
import com.kemaless.core.exception.error.RealmNotFoundException;
import com.kemaless.core.exception.error.RealmRoleAlreadyExistException;
import com.kemaless.core.exception.error.RealmRoleInUseException;
import com.kemaless.core.exception.error.RealmRoleNotFoundException;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmRole;
import com.kemaless.core.repository.ExternalRoleMappingRepository;
import com.kemaless.core.repository.RealmRepository;
import com.kemaless.core.repository.RealmRoleRepository;
import com.kemaless.core.service.interfaces.RealmRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealmRoleServiceImpl implements RealmRoleService {

    private final RealmRoleRepository roleRepository;
    private final RealmRepository realmRepository;
    private final ExternalRoleMappingRepository roleMappingRepository;
    private final AuditService auditService;

    @Transactional
    public RealmRoleResponse create(String realmCode, CreateRealmRoleRequest request) {
        log.info("Attempting to create role '{}' in realm '{}'", request.code(), realmCode);

        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> new RealmNotFoundException(realmCode));

        if (roleRepository.existsByRealmCodeAndCode(realmCode, request.code())) {
            log.warn("Role '{}' already exists in realm '{}'", request.code(), realmCode);
            throw new RealmRoleAlreadyExistException(realmCode, request.code());
        }

        RealmRole role = RealmRole.builder()
                .realm(realm)
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .build();

        roleRepository.save(role);
        auditService.write(realmCode, AuditAction.REALM_ROLE_CREATED.name(), "REALM_ROLE", role.getCode());

        log.info("Successfully created role '{}' in realm '{}'", request.code(), realmCode);

        return mapToDto(role);
    }

    @Transactional(readOnly = true)
    public List<RealmRoleResponse> findAll(String realmCode) {
        log.debug("Fetching roles for realm: {}", realmCode);

        return roleRepository.findAllByRealmCode(realmCode).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public RealmRoleResponse update(String realmCode, String roleCode, UpdateRealmRoleRequest request) {
        log.info("Updating role '{}' in realm '{}'", roleCode, realmCode);

        RealmRole role = getRoleOrThrow(realmCode, roleCode);

        if (StringUtils.hasText(request.name())) {
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }

        roleRepository.save(role);
        auditService.write(realmCode, AuditAction.REALM_ROLE_UPDATED.name(), "REALM_ROLE", role.getCode());

        return mapToDto(role);
    }

    @Transactional
    public void delete(String realmCode, String roleCode) {
        log.info("Attempting to delete role '{}' from realm '{}'", roleCode, realmCode);

        RealmRole role = getRoleOrThrow(realmCode, roleCode);

        boolean isUsedInMappings = roleMappingRepository.existsByRealmRole_Id(role.getId());

        if (isUsedInMappings) {
            log.warn("Cannot delete role '{}' - it is currently in use", roleCode);
            throw new RealmRoleInUseException(roleCode);
        }

        roleRepository.delete(role);
        auditService.write(realmCode, AuditAction.REALM_ROLE_DELETED.name(), "REALM_ROLE", roleCode);

        log.info("Successfully deleted role '{}' from realm '{}'", roleCode, realmCode);
    }

    private RealmRole getRoleOrThrow(String realmCode, String roleCode) {
        return roleRepository.findByRealmCodeAndCode(realmCode, roleCode)
                .orElseThrow(() -> new RealmRoleNotFoundException(realmCode, roleCode));
    }

    private RealmRoleResponse mapToDto(RealmRole role) {

        return RealmRoleResponse.builder()
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .createdAt(role.getCreatedAt())
                .build();
    }
}
