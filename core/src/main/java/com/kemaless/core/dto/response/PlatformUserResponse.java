package com.kemaless.core.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record PlatformUserResponse(
        UUID id,
        String username,
        String email,
        PlatformUserStatus status,
        GlobalRole globalRole,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String realmCode,
        OffsetDateTime createdAt
) {}
