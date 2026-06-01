package com.kemaless.core.service.impl;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.dto.request.AccessPolicyRequest;
import com.kemaless.core.dto.request.ConnectMiniAppRequest;
import com.kemaless.core.dto.request.UpdateRealmMiniAppRequest;
import com.kemaless.core.dto.response.AccessPolicyResponse;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.RealmMiniAppListItemResponse;
import com.kemaless.core.dto.response.RealmMiniAppResponse;
import com.kemaless.core.enums.AccessType;
import com.kemaless.core.enums.AppStatus;
import com.kemaless.core.enums.RealmMiniAppStatus;
import com.kemaless.core.exception.error.InvalidAccessPolicyException;
import com.kemaless.core.exception.error.RealmMiniAppAlreadyExistException;
import com.kemaless.core.exception.error.RealmMiniAppNotFoundException;
import com.kemaless.core.exception.error.RealmNotFoundException;
import com.kemaless.core.model.AccessPolicy;
import com.kemaless.core.model.MiniApp;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmMiniApp;
import com.kemaless.core.model.RealmRole;
import com.kemaless.core.repository.MiniAppRepository;
import com.kemaless.core.repository.RealmMiniAppRepository;
import com.kemaless.core.repository.RealmRepository;
import com.kemaless.core.repository.RealmRoleRepository;
import com.kemaless.core.service.interfaces.RealmMiniAppService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealmMiniAppServiceImpl implements RealmMiniAppService {

    private final RealmMiniAppRepository realmMiniAppRepository;
    private final RealmRepository realmRepository;
    private final MiniAppRepository miniAppRepository;
    private final RealmRoleRepository realmRoleRepository;
    private final AuditService auditService;

    @Transactional
    public RealmMiniAppResponse connect(String realmCode, ConnectMiniAppRequest request) {
        log.info("Connecting MiniApp '{}' to Realm '{}'", request.miniAppCode(), realmCode);

        Realm realm = realmRepository.findByCode(realmCode)
                .orElseThrow(() -> new RealmNotFoundException(realmCode));

        MiniApp globalApp = miniAppRepository.findByCode(request.miniAppCode())
                .orElseGet(() -> createGlobalMiniAppForConnect(request));

        if (realmMiniAppRepository.existsByRealmCodeAndMiniAppCodeAndStatusNot(realmCode, globalApp.getCode(), RealmMiniAppStatus.DELETED)) {
            throw new RealmMiniAppAlreadyExistException(realmCode, globalApp.getCode());
        }

        validateRolesForPolicy(realmCode, request.accessPolicy());

        String finalEntryUrl = StringUtils.hasText(request.entryUrl()) ? request.entryUrl() : globalApp.getDefaultEntryUrl();
        String finalBackendUrl = StringUtils.hasText(request.backendUrl()) ? request.backendUrl() : globalApp.getDefaultBackendUrl();

        if (!StringUtils.hasText(finalEntryUrl)) {
            throw new InvalidAccessPolicyException("Entry URL cannot be empty. Please provide it in request or set default in Global MiniApp.");
        }

        AccessPolicy accessPolicy = AccessPolicy.builder()
                .accessType(request.accessPolicy().accessType())
                .roleMatchMode(request.accessPolicy().roleMatchMode())
                .requiredRoles(request.accessPolicy().requiredRoles())
                .build();

        RealmMiniApp realmMiniApp = RealmMiniApp.builder()
                .realm(realm)
                .miniApp(globalApp)
                .status(RealmMiniAppStatus.DRAFT)
                .entryUrl(finalEntryUrl)
                .backendUrl(finalBackendUrl)
                .settings(request.settings())
                .accessPolicy(accessPolicy)
                .build();

        realmMiniAppRepository.save(realmMiniApp);
        auditService.write(realmCode, AuditAction.MINIAPP_CONNECTED_TO_REALM.name(), "REALM_MINIAPP", globalApp.getCode());

        return mapToDetailResponse(realmMiniApp);
    }

    private MiniApp createGlobalMiniAppForConnect(ConnectMiniAppRequest request) {
        if (!StringUtils.hasText(request.entryUrl())) {
            throw new InvalidAccessPolicyException(
                    "entryUrl is required when miniApp '%s' is created during connect".formatted(request.miniAppCode())
            );
        }

        MiniApp miniApp = MiniApp.builder()
                .code(request.miniAppCode())
                .name(request.miniAppCode())
                .baseUrl(request.entryUrl())
                .defaultEntryUrl(request.entryUrl())
                .defaultBackendUrl(request.backendUrl())
                .entryPath("/")
                .status(AppStatus.DRAFT)
                .build();

        miniApp = miniAppRepository.save(miniApp);
        auditService.write(null, AuditAction.MINIAPP_CREATED.name(), "MINIAPP", miniApp.getCode());
        return miniApp;
    }

    @Transactional(readOnly = true)
    public PageResponse<RealmMiniAppListItemResponse> findAll(String realmCode, String status, String search, Pageable pageable) {
        String normalizedStatus = StringUtils.hasText(status) ? status : "";
        String normalizedSearch = StringUtils.hasText(search) ? search : "";
        Page<RealmMiniApp> page = realmMiniAppRepository.findAllWithFilters(
                realmCode,
                normalizedStatus,
                normalizedSearch,
                pageable
        );
        var content = page.getContent().stream()
                .map(rma -> RealmMiniAppListItemResponse.builder()
                        .code(rma.getMiniApp().getCode())
                        .name(rma.getMiniApp().getName())
                        .status(rma.getStatus())
                        .entryUrl(rma.getEntryUrl())
                        .backendUrl(rma.getBackendUrl())
                        .accessType(rma.getAccessPolicy().getAccessType())
                        .build())
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public RealmMiniAppResponse getByCode(String realmCode, String miniAppCode) {
        return mapToDetailResponse(getRealmMiniAppOrThrow(realmCode, miniAppCode));
    }

    @Transactional
    public RealmMiniAppResponse update(String realmCode, String miniAppCode, UpdateRealmMiniAppRequest request) {
        log.info("Updating RealmMiniApp '{}' in Realm '{}'", miniAppCode, realmCode);
        RealmMiniApp rma = getRealmMiniAppOrThrow(realmCode, miniAppCode);

        if (StringUtils.hasText(request.entryUrl())) rma.setEntryUrl(request.entryUrl());
        if (StringUtils.hasText(request.backendUrl())) rma.setBackendUrl(request.backendUrl());
        if (request.settings() != null) rma.setSettings(request.settings());

        if (request.accessPolicy() != null) {
            validateRolesForPolicy(realmCode, request.accessPolicy());
            rma.getAccessPolicy().setAccessType(request.accessPolicy().accessType());
            rma.getAccessPolicy().setRoleMatchMode(request.accessPolicy().roleMatchMode());
            rma.getAccessPolicy().setRequiredRoles(request.accessPolicy().requiredRoles());
        }

        realmMiniAppRepository.save(rma);
        auditService.write(realmCode, AuditAction.REALM_MINIAPP_UPDATED.name(), "REALM_MINIAPP", miniAppCode);
        return mapToDetailResponse(rma);
    }

    @Transactional
    public void publish(String realmCode, String miniAppCode) {
        log.info("Publishing RealmMiniApp '{}' in Realm '{}'", miniAppCode, realmCode);
        RealmMiniApp rma = getRealmMiniAppOrThrow(realmCode, miniAppCode);

        if (!StringUtils.hasText(rma.getEntryUrl())) {
            throw new InvalidAccessPolicyException("Cannot publish: entryUrl is empty");
        }

        AccessPolicyRequest policyDto = new AccessPolicyRequest(
                rma.getAccessPolicy().getAccessType(),
                rma.getAccessPolicy().getRoleMatchMode(),
                rma.getAccessPolicy().getRequiredRoles()
        );
        validateRolesForPolicy(realmCode, policyDto);

        rma.setStatus(RealmMiniAppStatus.PUBLISHED);
        realmMiniAppRepository.save(rma);
        auditService.write(realmCode, AuditAction.MINIAPP_PUBLISHED.name(), "REALM_MINIAPP", miniAppCode);
    }

    @Transactional
    public void stop(String realmCode, String miniAppCode) {
        log.info("Stopping RealmMiniApp '{}' in Realm '{}'", miniAppCode, realmCode);
        RealmMiniApp rma = getRealmMiniAppOrThrow(realmCode, miniAppCode);

        rma.setStatus(RealmMiniAppStatus.STOPPED);
        realmMiniAppRepository.save(rma);
        auditService.write(realmCode, AuditAction.MINIAPP_STOPPED.name(), "REALM_MINIAPP", miniAppCode);
    }

    @Transactional
    public void delete(String realmCode, String miniAppCode) {
        log.info("Deleting (Soft) RealmMiniApp '{}' in Realm '{}'", miniAppCode, realmCode);
        RealmMiniApp rma = getRealmMiniAppOrThrow(realmCode, miniAppCode);

        rma.setStatus(RealmMiniAppStatus.DELETED);
        realmMiniAppRepository.save(rma);
        auditService.write(realmCode, AuditAction.MINIAPP_REMOVED_FROM_REALM.name(), "REALM_MINIAPP", miniAppCode);
    }

    private RealmMiniApp getRealmMiniAppOrThrow(String realmCode, String miniAppCode) {
        return realmMiniAppRepository.findByRealmCodeAndMiniAppCodeAndStatusNot(realmCode, miniAppCode, RealmMiniAppStatus.DELETED)
                .orElseThrow(() -> new RealmMiniAppNotFoundException(realmCode, miniAppCode));
    }

    private void validateRolesForPolicy(String realmCode, AccessPolicyRequest policy) {
        if (policy.accessType() == null) {
            throw new InvalidAccessPolicyException("accessType is required.");
        }
        if (policy.accessType() == AccessType.ROLE_BASED) {
            if (policy.roleMatchMode() == null) {
                throw new InvalidAccessPolicyException("ROLE_BASED access type requires roleMatchMode.");
            }
            if (policy.requiredRoles() == null || policy.requiredRoles().isEmpty()) {
                throw new InvalidAccessPolicyException("ROLE_BASED access type requires at least one required role.");
            }

            Set<String> existingRoles = realmRoleRepository.findAllByRealmCode(realmCode).stream()
                    .map(RealmRole::getCode)
                    .collect(Collectors.toSet());

            for (String requiredRole : policy.requiredRoles()) {
                if (!existingRoles.contains(requiredRole)) {
                    throw new InvalidAccessPolicyException("Required role '%s' does not exist in realm '%s'".formatted(requiredRole, realmCode));
                }
            }
        }
    }

    private RealmMiniAppResponse mapToDetailResponse(RealmMiniApp rma) {
        AccessPolicyResponse policyDto = new AccessPolicyResponse(
                rma.getAccessPolicy().getAccessType(),
                rma.getAccessPolicy().getRoleMatchMode(),
                rma.getAccessPolicy().getRequiredRoles()
        );

        return RealmMiniAppResponse.builder()
                .code(rma.getMiniApp().getCode())
                .name(rma.getMiniApp().getName())
                .status(rma.getStatus())
                .entryUrl(rma.getEntryUrl())
                .backendUrl(rma.getBackendUrl())
                .settings(rma.getSettings())
                .accessPolicy(policyDto)
                .build();
    }

    @Override
    @Transactional
    public RealmMiniAppResponse updateAccessPolicy(String realmCode, String miniAppCode, AccessPolicyRequest request) {
        log.info("Updating access policy for miniApp: {} in realm: {}. Target type: {}",
                miniAppCode, realmCode, request.accessType());

        RealmMiniApp rma = getRealmMiniAppOrThrow(realmCode, miniAppCode);

        validatePolicyLogic(realmCode, request);

        rma.getAccessPolicy().setAccessType(request.accessType());

        if (request.accessType() == AccessType.ROLE_BASED) {
            rma.getAccessPolicy().setRoleMatchMode(request.roleMatchMode());
            rma.getAccessPolicy().setRequiredRoles(request.requiredRoles());
        } else {
            rma.getAccessPolicy().setRoleMatchMode(null);
            rma.getAccessPolicy().setRequiredRoles(null);
        }

        realmMiniAppRepository.save(rma);
        auditService.write(realmCode, AuditAction.ACCESS_POLICY_UPDATED.name(), "REALM_MINIAPP", miniAppCode);

        return mapToDetailResponse(rma);
    }

    private void validatePolicyLogic(String realmCode, AccessPolicyRequest policy) {
        if (policy.accessType() == AccessType.ROLE_BASED) {
            if (policy.roleMatchMode() == null) {
                throw new InvalidAccessPolicyException("roleMatchMode is required for ROLE_BASED access");
            }

            if (policy.requiredRoles() == null || policy.requiredRoles().isEmpty()) {
                throw new InvalidAccessPolicyException("requiredRoles cannot be empty for ROLE_BASED access");
            }

            Set<String> existingRoleCodes = realmRoleRepository.findAllByRealmCode(realmCode).stream()
                    .map(RealmRole::getCode)
                    .collect(Collectors.toSet());

            for (String role : policy.requiredRoles()) {
                if (!existingRoleCodes.contains(role)) {
                    throw new InvalidAccessPolicyException("Role '" + role + "' not found in current realm");
                }
            }
        }
    }
}
