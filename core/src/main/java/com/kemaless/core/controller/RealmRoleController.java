package com.kemaless.core.controller;

import com.kemaless.core.dto.request.CreateRealmRoleRequest;
import com.kemaless.core.dto.request.UpdateRealmRoleRequest;
import com.kemaless.core.dto.response.RealmRoleResponse;
import com.kemaless.core.service.interfaces.RealmRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/realms/{realmCode}/roles")
@RequiredArgsConstructor
public class RealmRoleController {

    private final RealmRoleService roleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmRoles(authentication, #realmCode)")
    public RealmRoleResponse createRole(
            @PathVariable String realmCode,
            @Valid @RequestBody CreateRealmRoleRequest request) {
        return roleService.create(realmCode, request);
    }

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public List<RealmRoleResponse> getRoles(@PathVariable String realmCode) {
        return roleService.findAll(realmCode);
    }

    @PatchMapping("/{roleCode}")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmRoles(authentication, #realmCode)")
    public RealmRoleResponse updateRole(
            @PathVariable String realmCode,
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRealmRoleRequest request) {
        return roleService.update(realmCode, roleCode, request);
    }

    @DeleteMapping("/{roleCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmRoles(authentication, #realmCode)")
    public void deleteRole(
            @PathVariable String realmCode,
            @PathVariable String roleCode) {
        roleService.delete(realmCode, roleCode);
    }
}
