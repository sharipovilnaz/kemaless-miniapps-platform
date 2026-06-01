package com.kemaless.core.service.impl;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.service.AuditService;
import com.kemaless.core.dto.request.CreateMiniAppRequest;
import com.kemaless.core.dto.request.UpdateMiniAppRequest;
import com.kemaless.core.dto.response.MiniAppResponse;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.enums.AppStatus;
import com.kemaless.core.exception.error.MiniAppAlreadyExistException;
import com.kemaless.core.exception.error.MiniAppInUseException;
import com.kemaless.core.exception.error.MiniAppNotFoundException;
import com.kemaless.core.model.MiniApp;
import com.kemaless.core.repository.MiniAppRepository;
import com.kemaless.core.repository.RealmMiniAppRepository;
import com.kemaless.core.service.interfaces.MiniAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MiniAppServiceImpl implements MiniAppService{

    private final MiniAppRepository miniAppRepository;
    private final RealmMiniAppRepository realmMiniAppRepository;
    private final AuditService auditService;

    @Transactional
    public MiniAppResponse create(CreateMiniAppRequest request) {
        log.info("Attempting to create MiniApp: {}", request.code());

        if (miniAppRepository.existsByCode(request.code())) {
            log.warn("MiniApp creation failed. Code '{}' already exists.", request.code());
            throw new MiniAppAlreadyExistException(request.code());
        }

        MiniApp miniApp = MiniApp.builder()
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .iconUrl(request.iconUrl())
                .baseUrl(request.defaultEntryUrl())
                .defaultEntryUrl(request.defaultEntryUrl())
                .defaultBackendUrl(request.defaultBackendUrl())
                .entryPath("/")
                .status(AppStatus.DRAFT)
                .metadata(request.metadata())
                .build();

        miniAppRepository.save(miniApp);
        auditService.write(null, AuditAction.MINIAPP_CREATED.name(), "MINIAPP", miniApp.getCode());

        log.info("Successfully created MiniApp: {}", request.code());
        return mapToDto(miniApp);
    }

    @Transactional(readOnly = true)
    public PageResponse<MiniAppResponse> findAll(String search, Pageable pageable) {
        log.debug("Fetching MiniApps catalog. Search: '{}'", search);

        Page<MiniApp> page = miniAppRepository.findAllWithFilters(search, pageable);
        var content = page.getContent().stream().map(this::mapToDto).toList();

        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MiniAppResponse getByCode(String code) {
        return mapToDto(getMiniAppOrThrow(code));
    }

    @Transactional
    public MiniAppResponse update(String code, UpdateMiniAppRequest request) {
        log.info("Updating MiniApp: {}", code);
        MiniApp miniApp = getMiniAppOrThrow(code);

        if (StringUtils.hasText(request.name())) miniApp.setName(request.name());
        if (request.description() != null) miniApp.setDescription(request.description());
        if (request.iconUrl() != null) miniApp.setIconUrl(request.iconUrl());
        if (request.defaultEntryUrl() != null) miniApp.setDefaultEntryUrl(request.defaultEntryUrl());
        if (request.defaultBackendUrl() != null) miniApp.setDefaultBackendUrl(request.defaultBackendUrl());
        if (request.metadata() != null) miniApp.setMetadata(request.metadata());

        miniAppRepository.save(miniApp);
        auditService.write(null, AuditAction.MINIAPP_UPDATED.name(), "MINIAPP", miniApp.getCode());

        return mapToDto(miniApp);
    }

    @Transactional
    public void delete(String code) {
        log.info("Attempting to delete MiniApp: {}", code);
        MiniApp miniApp = getMiniAppOrThrow(code);

        if (realmMiniAppRepository.existsByMiniApp_Id(miniApp.getId())) {
            log.warn("Cannot delete MiniApp '{}' - it is connected to one or more realms", code);
            throw new MiniAppInUseException(code);
        }

        miniAppRepository.delete(miniApp);
        auditService.write(null, AuditAction.MINIAPP_DELETED.name(), "MINIAPP", miniApp.getCode());

        log.info("Successfully deleted MiniApp: {}", code);
    }

    private MiniApp getMiniAppOrThrow(String code) {
        return miniAppRepository.findByCode(code)
                .orElseThrow(() -> new MiniAppNotFoundException(code));
    }

    private MiniAppResponse mapToDto(MiniApp miniApp) {
        return MiniAppResponse.builder()
                .id(miniApp.getId())
                .code(miniApp.getCode())
                .name(miniApp.getName())
                .description(miniApp.getDescription())
                .iconUrl(miniApp.getIconUrl())
                .defaultEntryUrl(miniApp.getDefaultEntryUrl())
                .defaultBackendUrl(miniApp.getDefaultBackendUrl())
                .metadata(miniApp.getMetadata())
                .createdAt(miniApp.getCreatedAt())
                .build();
    }
}
