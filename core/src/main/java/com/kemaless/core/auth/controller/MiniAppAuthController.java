package com.kemaless.core.auth.controller;

import com.kemaless.core.auth.dto.api.MiniAppRefreshRequest;
import com.kemaless.core.auth.dto.api.TokenResponse;
import com.kemaless.core.auth.dto.api.TokenIntrospectionRequest;
import com.kemaless.core.auth.dto.api.TokenIntrospectionResponse;
import com.kemaless.core.auth.service.SsoExchangeService;
import com.kemaless.core.auth.service.TokenIntrospectionService;
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
public class MiniAppAuthController {

    private final SsoExchangeService ssoExchangeService;
    private final TokenIntrospectionService tokenIntrospectionService;

    @PostMapping("/refresh")
    public TokenResponse refresh(
            @PathVariable String realmCode,
            @Valid @RequestBody MiniAppRefreshRequest request
    ) {
        return ssoExchangeService.refresh(realmCode, request);
    }

    @PostMapping("/introspect")
    public TokenIntrospectionResponse introspect(
            @PathVariable String realmCode,
            @Valid @RequestBody TokenIntrospectionRequest request
    ) {
        return tokenIntrospectionService.introspect(realmCode, request.token());
    }
}
