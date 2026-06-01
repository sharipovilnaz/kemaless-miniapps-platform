package com.kemaless.core.service.impl;

import com.kemaless.core.dto.response.RuntimeMiniAppResponse;
import com.kemaless.core.repository.RealmMiniAppRepository;
import com.kemaless.core.service.interfaces.RuntimeCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuntimeCatalogServiceImpl implements RuntimeCatalogService {

    private final RealmMiniAppRepository realmMiniAppRepository;

    @Override
    @Transactional(readOnly = true)
    public List<RuntimeMiniAppResponse> getCatalog(String realmCode) {
        return realmMiniAppRepository.findAllPublishedInActiveRealm(realmCode).stream()
                .map(rma -> RuntimeMiniAppResponse.builder()
                        .code(rma.getMiniApp().getCode())
                        .name(rma.getMiniApp().getName())
                        .description(rma.getMiniApp().getDescription())
                        .iconUrl(rma.getMiniApp().getIconUrl())
                        .entryUrl(rma.getEntryUrl())
                        .metadata(rma.getMiniApp().getMetadata())
                        .build())
                .collect(Collectors.toList());
    }
}
