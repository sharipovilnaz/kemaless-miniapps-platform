package com.kemaless.core.auth.dto.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

public record LaunchMiniAppRequest(
        @Valid User user,
        Map<String, Object> device,
        Map<String, Object> context
) {
    public record User(
            String externalUserId,
            String email,
            List<String> roles,
            Map<String, Object> attributes
    ) {
    }
}
