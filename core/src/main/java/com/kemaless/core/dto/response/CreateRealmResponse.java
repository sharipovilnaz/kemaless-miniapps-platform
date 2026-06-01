package com.kemaless.core.dto.response;

import com.kemaless.core.enums.RealmStatus;
import lombok.Builder;

@Builder
public record CreateRealmResponse(
        String code,
        String name,
        String description,
        RealmStatus status,
        String clientId,
        String clientSecret
) {}
