package com.kemaless.core.auth.dto.api;

public record LaunchMiniAppResponse(
        String launchUrl,
        long ticketExpiresIn
) {
}
