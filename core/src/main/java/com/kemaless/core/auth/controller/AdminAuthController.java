package com.kemaless.core.auth.controller;

import com.kemaless.core.auth.dto.api.AdminAuthResponse;
import com.kemaless.core.auth.dto.api.AdminLoginRequest;
import com.kemaless.core.auth.dto.api.AdminLogoutRequest;
import com.kemaless.core.auth.dto.api.RefreshTokenRequest;
import com.kemaless.core.auth.dto.api.TokenResponse;
import com.kemaless.core.auth.dto.AdminJwtPrincipal;
import com.kemaless.core.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public AdminAuthResponse login(@Valid @RequestBody AdminLoginRequest request) {
        return adminAuthService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return adminAuthService.refresh(request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    public void logout(
            @AuthenticationPrincipal AdminJwtPrincipal principal,
            @Valid @RequestBody AdminLogoutRequest request
    ) {
        adminAuthService.logout(principal.userId(), request);
    }
}
