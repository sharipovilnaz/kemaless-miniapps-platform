package com.kemaless.core.controller;

import com.kemaless.core.dto.request.CreateRoleMappingRequest;
import com.kemaless.core.dto.request.UpdateRoleMappingRequest;
import com.kemaless.core.dto.response.RoleMappingResponse;
import com.kemaless.core.service.interfaces.ExternalRoleMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/realms/{realmCode}/role-mappings")
@RequiredArgsConstructor
public class ExternalRoleMappingController {

    private final ExternalRoleMappingService mappingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@adminPermissionEvaluator.canManageRoleMappings(authentication, #realmCode)")
    public RoleMappingResponse createMapping(
            @PathVariable String realmCode,
            @Valid @RequestBody CreateRoleMappingRequest request) {
        return mappingService.createMapping(realmCode, request);
    }

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public List<RoleMappingResponse> getMappings(@PathVariable String realmCode) {
        return mappingService.getMappings(realmCode);
    }

    @PatchMapping("/{mappingId}")
    @PreAuthorize("@adminPermissionEvaluator.canManageRoleMappings(authentication, #realmCode)")
    public RoleMappingResponse updateMapping(
            @PathVariable String realmCode,
            @PathVariable UUID mappingId,
            @Valid @RequestBody UpdateRoleMappingRequest request) {
        return mappingService.updateMapping(realmCode, mappingId, request);
    }

    @DeleteMapping("/{mappingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@adminPermissionEvaluator.canManageRoleMappings(authentication, #realmCode)")
    public void deleteMapping(
            @PathVariable String realmCode,
            @PathVariable UUID mappingId) {
        mappingService.deleteMapping(realmCode, mappingId);
    }
}
