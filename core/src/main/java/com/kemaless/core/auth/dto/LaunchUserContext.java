package com.kemaless.core.auth.dto;

import java.util.List;
import java.util.Map;

public record LaunchUserContext(
        String externalUserId,
        String email,
        List<String> externalRoles,
        Map<String, Object> attributes
) {
}
