package com.kemaless.core.controller;

import com.kemaless.core.dto.request.AccessPolicyRequest;
import com.kemaless.core.dto.request.ConnectMiniAppRequest;
import com.kemaless.core.dto.request.UpdateRealmMiniAppRequest;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.RealmMiniAppListItemResponse;
import com.kemaless.core.dto.response.RealmMiniAppResponse;
import com.kemaless.core.service.interfaces.RealmMiniAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/realms/{realmCode}/miniapps")
@RequiredArgsConstructor
public class RealmMiniAppController {

    private final RealmMiniAppService realmMiniAppService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmMiniApps(authentication, #realmCode)")
    public RealmMiniAppResponse connectMiniApp(
            @PathVariable String realmCode,
            @Valid @RequestBody ConnectMiniAppRequest request) {
        return realmMiniAppService.connect(realmCode, request);
    }

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public PageResponse<RealmMiniAppListItemResponse> getRealmMiniApps(
            @PathVariable String realmCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return realmMiniAppService.findAll(realmCode, status, search, pageable);
    }

    @GetMapping("/{miniAppCode}")
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public RealmMiniAppResponse getRealmMiniApp(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode) {
        return realmMiniAppService.getByCode(realmCode, miniAppCode);
    }

    @PatchMapping("/{miniAppCode}")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmMiniApps(authentication, #realmCode)")
    public RealmMiniAppResponse updateRealmMiniApp(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode,
            @Valid @RequestBody UpdateRealmMiniAppRequest request) {
        return realmMiniAppService.update(realmCode, miniAppCode, request);
    }

    @PostMapping("/{miniAppCode}/publish")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmMiniApps(authentication, #realmCode)")
    public void publishMiniApp(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode) {
        realmMiniAppService.publish(realmCode, miniAppCode);
    }

    @PostMapping("/{miniAppCode}/stop")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmMiniApps(authentication, #realmCode)")
    public void stopMiniApp(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode) {
        realmMiniAppService.stop(realmCode, miniAppCode);
    }

    @DeleteMapping("/{miniAppCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmMiniApps(authentication, #realmCode)")
    public void deleteRealmMiniApp(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode) {
        realmMiniAppService.delete(realmCode, miniAppCode);
    }

    @PutMapping("/{miniAppCode}/access-policy")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmMiniApps(authentication, #realmCode)")
    public RealmMiniAppResponse updateAccessPolicy(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode,
            @Valid @RequestBody AccessPolicyRequest request) {
        return realmMiniAppService.updateAccessPolicy(realmCode, miniAppCode, request);
    }
}
