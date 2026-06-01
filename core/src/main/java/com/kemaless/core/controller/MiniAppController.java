package com.kemaless.core.controller;

import com.kemaless.core.dto.request.CreateMiniAppRequest;
import com.kemaless.core.dto.request.UpdateMiniAppRequest;
import com.kemaless.core.dto.response.MiniAppResponse;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.service.impl.MiniAppServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/miniapps")
@RequiredArgsConstructor
public class MiniAppController {

    private final MiniAppServiceImpl miniAppService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@adminPermissionEvaluator.canManageMiniAppCatalog(authentication)")
    public MiniAppResponse createMiniApp(@Valid @RequestBody CreateMiniAppRequest request) {
        return miniAppService.create(request);
    }

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canViewMiniAppCatalog(authentication)")
    public PageResponse<MiniAppResponse> getMiniApps(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return miniAppService.findAll(search, pageable);
    }

    @GetMapping("/{miniAppCode}")
    @PreAuthorize("@adminPermissionEvaluator.canViewMiniAppCatalog(authentication)")
    public MiniAppResponse getMiniApp(@PathVariable String miniAppCode) {
        return miniAppService.getByCode(miniAppCode);
    }

    @PatchMapping("/{miniAppCode}")
    @PreAuthorize("@adminPermissionEvaluator.canManageMiniAppCatalog(authentication)")
    public MiniAppResponse updateMiniApp(
            @PathVariable String miniAppCode,
            @Valid @RequestBody UpdateMiniAppRequest request) {
        return miniAppService.update(miniAppCode, request);
    }

    @DeleteMapping("/{miniAppCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@adminPermissionEvaluator.canManageMiniAppCatalog(authentication)")
    public void deleteMiniApp(@PathVariable String miniAppCode) {
        miniAppService.delete(miniAppCode);
    }
}
