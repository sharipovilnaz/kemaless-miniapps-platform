package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.response.RuntimeMiniAppResponse;

import java.util.List;

public interface RuntimeCatalogService {
    List<RuntimeMiniAppResponse> getCatalog(String realmCode);
}
