package com.kemaless.core.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ConnectMiniAppRequest(
        @NotBlank String miniAppCode,
        String entryUrl,
        String backendUrl,
        Map<String, Object> settings,
        @Valid @NotNull AccessPolicyRequest accessPolicy
) {}
