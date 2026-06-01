package com.kemaless.core.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleMappingRequest(
        @NotBlank String realmRoleCode
) {}
