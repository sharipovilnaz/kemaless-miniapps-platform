package com.kemaless.miniappsdk.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class TokenIntrospectionResult {
    private boolean active;
    private String type;
    private String realmCode;
    private String miniAppCode;
    private String externalUserId;
    private List<String> realmRoles;
    private Instant expiresAt;
}
