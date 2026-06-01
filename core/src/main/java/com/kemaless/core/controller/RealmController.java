package com.kemaless.core.controller;

import com.kemaless.core.dto.request.CreateRealmRequest;
import com.kemaless.core.dto.request.UpdateRealmRequest;
import com.kemaless.core.dto.response.CreateRealmResponse;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.RealmDetailsResponse;
import com.kemaless.core.dto.response.RealmListItemResponse;
import com.kemaless.core.service.interfaces.RealmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/realms")
@RequiredArgsConstructor
public class RealmController {

    private final RealmService realmService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@adminPermissionEvaluator.canCreateRealm(authentication)")
    public CreateRealmResponse create(@Valid @RequestBody CreateRealmRequest request) {
        return realmService.create(request);
    }

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canListRealms(authentication)")
    public PageResponse<RealmListItemResponse> getList(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return realmService.findAll(search, status, pageable);
    }

    @GetMapping("/{realmCode}")
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public RealmDetailsResponse getOne(@PathVariable String realmCode) {
        return realmService.getByCode(realmCode);
    }

    @PatchMapping("/{realmCode}")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealm(authentication, #realmCode)")
    public void update(@PathVariable String realmCode, @RequestBody UpdateRealmRequest request) {
        realmService.update(realmCode, request);
    }

    @PostMapping("/{realmCode}/disable")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealm(authentication, #realmCode)")
    public void disable(@PathVariable String realmCode) {
        realmService.changeStatus(realmCode, false);
    }

    @PostMapping("/{realmCode}/enable")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealm(authentication, #realmCode)")
    public void enable(@PathVariable String realmCode) {
        realmService.changeStatus(realmCode, true);
    }

    @DeleteMapping("/{realmCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealm(authentication, #realmCode)")
    public void delete(@PathVariable String realmCode) {
        realmService.delete(realmCode);
    }
}
