package com.kemaless.paymentminiapp.web;

import com.kemaless.paymentminiapp.sso.SsoExchangeRequest;
import com.kemaless.paymentminiapp.sso.SsoExchangeService;
import com.kemaless.paymentminiapp.sso.SsoSessionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionExchangeController {

    private final SsoExchangeService ssoExchangeService;

    public SessionExchangeController(SsoExchangeService ssoExchangeService) {
        this.ssoExchangeService = ssoExchangeService;
    }

    @PostMapping("/api/session/exchange")
    public SsoSessionResponse exchange(@Valid @RequestBody SsoExchangeRequest request) {
        return ssoExchangeService.exchange(request.ticket(), request.realmCode(), request.miniAppCode());
    }
}
