package com.kemaless.core.service.impl;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import com.kemaless.core.auth.repository.PlatformUserRepository;
import com.kemaless.core.dto.request.ChangeAccessRequest;
import com.kemaless.core.dto.request.CreateUserRequest;
import com.kemaless.core.dto.request.UpdateUserRequest;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.PlatformUserResponse;
import com.kemaless.core.exception.error.PlatformUserAlreadyExistException;
import com.kemaless.core.exception.error.PlatformUserNotFoundException;
import com.kemaless.core.exception.error.RealmNotFoundException;
import com.kemaless.core.model.Realm;
import com.kemaless.core.repository.RealmRepository;
import com.kemaless.core.service.interfaces.PlatformUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformUserServiceImpl implements PlatformUserService {

    private final PlatformUserRepository userRepository;
    private final RealmRepository realmRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    @Transactional
    public PlatformUserResponse create(CreateUserRequest request) {
        log.info("Attempting to create user: {}", request.username());

        if (userRepository.existsByUsername(request.username())) {
            log.warn("Username already exists: {}", request.username());
            throw new PlatformUserAlreadyExistException(request.username());
        }
        if (StringUtils.hasText(request.email()) && userRepository.existsByEmail(request.email())) {
            log.warn("Email already exists: {}", request.email());
            throw new PlatformUserAlreadyExistException(request.username());
        }

        Realm realm = validateAndGetRealm(request.globalRole(), request.realmCode());
        String passwordHash = passwordEncoder.encode(request.password());

        PlatformUser user = PlatformUser.builder()
                .id(UUID.randomUUID())
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordHash)
                .status(PlatformUserStatus.ACTIVE)
                .globalRole(request.globalRole())
                .realm(realm)
                .build();

        user = userRepository.save(user);
        auditService.write(realmCode(user), AuditAction.USER_CREATED.name(), "PLATFORM_USER", user.getId().toString());

        log.info("Successfully created user: {} with role {}", request.username(), request.globalRole());
        return mapToDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlatformUserResponse> findAll(
            String search,
            PlatformUserStatus status,
            GlobalRole role,
            String realmCode,
            Pageable pageable
    ) {
        log.debug("Fetching users. Search: '{}', Status: '{}', Role: '{}', Realm: '{}'", search, status, role, realmCode);

        Page<PlatformUser> page = userRepository.findAllWithFilters(search, status, role, realmCode, pageable);
        var content = page.getContent().stream().map(this::mapToDto).toList();

        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformUserResponse getById(UUID userId) {
        log.debug("Fetching user by ID: {}", userId);
        return mapToDto(getUserOrThrow(userId));
    }

    @Override
    @Transactional
    public PlatformUserResponse update(UUID userId, UpdateUserRequest request) {
        log.info("Updating user ID: {}", userId);
        PlatformUser user = getUserOrThrow(userId);

        if (StringUtils.hasText(request.email()) && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                log.warn("Cannot update. Email already exists: {}", request.email());
                throw new PlatformUserAlreadyExistException(request.email());
            }
            user.setEmail(request.email());
        }

        if (request.status() != null) {
            user.setStatus(request.status());
        }

        userRepository.save(user);
        AuditAction action = request.status() == PlatformUserStatus.BLOCKED
                ? AuditAction.USER_BLOCKED
                : request.status() == PlatformUserStatus.ACTIVE ? AuditAction.USER_UNBLOCKED : AuditAction.USER_UPDATED;
        auditService.write(realmCode(user), action.name(), "PLATFORM_USER", user.getId().toString());

        log.info("Successfully updated user ID: {}", userId);
        return mapToDto(user);
    }

    @Override
    @Transactional
    public PlatformUserResponse changeAccess(UUID userId, ChangeAccessRequest request) {
        log.info("Changing access for user ID: {} to Role: {}, Realm: {}", userId, request.globalRole(), request.realmCode());

        PlatformUser user = getUserOrThrow(userId);
        Realm newRealm = validateAndGetRealm(request.globalRole(), request.realmCode());

        user.setGlobalRole(request.globalRole());
        user.setRealm(newRealm);

        userRepository.save(user);
        auditService.write(realmCode(user), AuditAction.USER_REALM_ROLES_UPDATED.name(), "PLATFORM_USER", user.getId().toString());

        log.info("Successfully changed access for user ID: {}", userId);
        return mapToDto(user);
    }

    private Realm validateAndGetRealm(GlobalRole role, String realmCode) {
        if (role == GlobalRole.ROOT) {
            if (StringUtils.hasText(realmCode)) {
                log.warn("Validation failed: ROOT user cannot have a realmCode ({})", realmCode);
                throw new IllegalArgumentException("ROOT user cannot be assigned to a specific realm.");
            }
            return null;
        } else {
            if (!StringUtils.hasText(realmCode)) {
                log.warn("Validation failed: Role {} requires a realmCode", role);
                throw new IllegalArgumentException("Role " + role + " requires a realmCode.");
            }
            return realmRepository.findByCode(realmCode)
                    .orElseThrow(() -> new RealmNotFoundException(realmCode));
        }
    }

    private PlatformUser getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("User not found by ID: {}", id);
                    return new PlatformUserNotFoundException(id);
                });
    }

    private PlatformUserResponse mapToDto(PlatformUser user) {
        OffsetDateTime createdAt = user.getCreatedAt() == null
                ? null
                : OffsetDateTime.ofInstant(user.getCreatedAt(), ZoneOffset.UTC);
        return PlatformUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .globalRole(user.getGlobalRole())
                .realmCode(user.getRealm() != null ? user.getRealm().getCode() : null)
                .createdAt(createdAt)
                .build();
    }

    private String realmCode(PlatformUser user) {
        return user.getRealm() != null ? user.getRealm().getCode() : null;
    }
}
