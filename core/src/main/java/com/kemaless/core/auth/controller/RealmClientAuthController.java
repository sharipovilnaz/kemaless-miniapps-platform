package com.kemaless.core.auth.controller;

import com.kemaless.core.auth.dto.api.HostServiceTokenResponse;
import com.kemaless.core.auth.dto.api.RealmClientTokenRequest;
import com.kemaless.core.auth.service.RealmClientAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realms/{realmCode}/auth")
@RequiredArgsConstructor
public class RealmClientAuthController {

    private final RealmClientAuthService realmClientAuthService;

    @PostMapping("/token")
    public HostServiceTokenResponse token(
            @PathVariable String realmCode,
            @Valid @RequestBody RealmClientTokenRequest request
    ) {
        return realmClientAuthService.issueToken(realmCode, request);
    }
}
