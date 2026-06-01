package com.kemaless.core.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record CreateRealmClientResponse(
        String clientId,
        String clientSecret
) {}
