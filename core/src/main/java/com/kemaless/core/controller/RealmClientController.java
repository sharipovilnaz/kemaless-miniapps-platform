package com.kemaless.core.controller;

import com.kemaless.core.dto.request.CreateRealmClientRequest;
import com.kemaless.core.dto.response.CreateRealmClientResponse;
import com.kemaless.core.dto.response.RealmClientResponse;
import com.kemaless.core.dto.response.RotateSecretResponse;
import com.kemaless.core.service.interfaces.RealmClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/realms/{realmCode}/clients")
@RequiredArgsConstructor
public class RealmClientController {

    private final RealmClientService clientService;

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public List<RealmClientResponse> getClients(@PathVariable String realmCode) {
        return clientService.findAllByRealm(realmCode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmClients(authentication, #realmCode)")
    public CreateRealmClientResponse createClient(
            @PathVariable String realmCode,
            @Valid @RequestBody CreateRealmClientRequest request) {
        return clientService.create(realmCode, request);
    }

    @PostMapping("/{clientId}/rotate-secret")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmClients(authentication, #realmCode)")
    public RotateSecretResponse rotateSecret(
            @PathVariable String realmCode,
            @PathVariable String clientId) {
        return clientService.rotateSecret(realmCode, clientId);
    }

    @PostMapping("/{clientId}/block")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmClients(authentication, #realmCode)")
    public void blockClient(@PathVariable String realmCode, @PathVariable String clientId) {
        clientService.changeStatus(realmCode, clientId, false);
    }

    @PostMapping("/{clientId}/unblock")
    @PreAuthorize("@adminPermissionEvaluator.canManageRealmClients(authentication, #realmCode)")
    public void unblockClient(@PathVariable String realmCode, @PathVariable String clientId) {
        clientService.changeStatus(realmCode, clientId, true);
    }
}
