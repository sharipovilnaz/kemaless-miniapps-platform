package com.kemaless.core.dto.response;

import lombok.Builder;

@Builder
public record RotateSecretResponse(
        String clientId,
        String clientSecret
) {}
