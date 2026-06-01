package com.kemaless.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;

import java.util.Map;
public record CreateMiniAppRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9\\-]+$", message = "Code must contain only lowercase latin letters, numbers, and hyphens")
        String code,
        @NotBlank String name,
        String description,
        @URL(message = "Invalid icon URL format") String iconUrl,
        @NotBlank @URL(message = "Invalid entry URL format") String defaultEntryUrl,
        @URL(message = "Invalid backend URL format") String defaultBackendUrl,

        Map<String, Object> metadata
) {}
