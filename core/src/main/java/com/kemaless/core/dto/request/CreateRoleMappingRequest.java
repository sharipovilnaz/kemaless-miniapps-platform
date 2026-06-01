package com.kemaless.core.dto.request;

import com.kemaless.core.enums.RoleSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleMappingRequest(
        @NotBlank String externalRole,
        @NotBlank String realmRoleCode,
        @NotNull RoleSource source
) {}
