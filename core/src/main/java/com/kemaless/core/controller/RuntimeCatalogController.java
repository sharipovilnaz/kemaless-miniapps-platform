package com.kemaless.core.controller;

import com.kemaless.core.dto.response.RuntimeMiniAppResponse;
import com.kemaless.core.service.interfaces.RuntimeCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/realms/{realmCode}/miniapps/catalog")
@RequiredArgsConstructor
public class RuntimeCatalogController {

    private final RuntimeCatalogService runtimeCatalogService;

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canViewRuntimeCatalog(authentication, #realmCode)")
    public List<RuntimeMiniAppResponse> getCatalog(@PathVariable String realmCode) {
        return runtimeCatalogService.getCatalog(realmCode);
    }
}
