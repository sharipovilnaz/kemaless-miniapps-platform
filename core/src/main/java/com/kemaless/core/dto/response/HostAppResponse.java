package com.kemaless.core.dto.response;

import com.kemaless.core.enums.HostStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record HostAppResponse(
        UUID id,
        String name,
        String description,
        String clientId,
        HostStatus status,
        List<String> allowedOrigins,
        List<String> allowedRedirectUrls,
        List<String> allowedPackageNames,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
