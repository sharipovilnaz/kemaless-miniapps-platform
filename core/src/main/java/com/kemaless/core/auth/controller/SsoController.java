package com.kemaless.core.auth.controller;

import com.kemaless.core.auth.dto.api.SsoExchangeRequest;
import com.kemaless.core.auth.dto.api.SsoExchangeResponse;
import com.kemaless.core.auth.service.SsoExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realms/{realmCode}/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoExchangeService ssoExchangeService;

    @PostMapping("/exchange")
    public SsoExchangeResponse exchange(
            @PathVariable String realmCode,
            @Valid @RequestBody SsoExchangeRequest request
    ) {
        return ssoExchangeService.exchange(realmCode, request);
    }
}
