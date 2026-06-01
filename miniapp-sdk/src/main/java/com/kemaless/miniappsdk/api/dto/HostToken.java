package com.kemaless.miniappsdk.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HostToken {
    private String accessToken;
    private long expiresIn;
    private String tokenType;
}
