package com.kemaless.core.auth.controller;

import com.kemaless.core.auth.dto.RealmClientJwtPrincipal;
import com.kemaless.core.auth.dto.api.LaunchMiniAppRequest;
import com.kemaless.core.auth.dto.api.LaunchMiniAppResponse;
import com.kemaless.core.auth.service.LaunchMiniAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realms/{realmCode}/miniapps/{miniAppCode}")
@RequiredArgsConstructor
public class MiniAppLaunchController {

    private final LaunchMiniAppService launchMiniAppService;

    @PostMapping("/launch")
    public LaunchMiniAppResponse launch(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode,
            @AuthenticationPrincipal RealmClientJwtPrincipal clientPrincipal,
            @Valid @RequestBody LaunchMiniAppRequest request
    ) {
        return launchMiniAppService.launch(realmCode, miniAppCode, clientPrincipal, request);
    }
}
