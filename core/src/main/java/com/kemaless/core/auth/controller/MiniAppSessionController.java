package com.kemaless.core.auth.controller;

import com.kemaless.core.auth.dto.api.HostSessionLogoutRequest;
import com.kemaless.core.auth.dto.api.SessionLogoutResponse;
import com.kemaless.core.auth.service.MiniAppSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realms/{realmCode}/sessions")
@RequiredArgsConstructor
public class MiniAppSessionController {

    private final MiniAppSessionService miniAppSessionService;

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('REALM_CLIENT_TOKEN') and authentication.principal.realmCode() == #realmCode")
    public SessionLogoutResponse logoutByHost(
            @PathVariable String realmCode,
            @Valid @RequestBody HostSessionLogoutRequest request
    ) {
        return miniAppSessionService.logoutByHost(realmCode, request);
    }
}
