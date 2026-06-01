package com.kemaless.core.auth.dto;

import java.util.Map;
import java.util.List;

public record AccessContext(
        String externalUserId,
        String email,
        List<String> realmRoles,
        Map<String, Object> attributes
) {
}
