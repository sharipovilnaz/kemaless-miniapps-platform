package com.kemaless.miniappsdk.api.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HostTokenResponse {
    private String accessToken;
    private long expiresIn;
    private String tokenType;
}
