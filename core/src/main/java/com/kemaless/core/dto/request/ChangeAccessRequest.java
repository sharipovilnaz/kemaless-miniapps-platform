package com.kemaless.core.dto.request;

import com.kemaless.core.auth.enums.GlobalRole;
import jakarta.validation.constraints.NotNull;

public record ChangeAccessRequest(
        @NotNull GlobalRole globalRole,
        String realmCode
) {}
