package com.kemaless.miniappsdk.api.dto.response;


import lombok.Data;

@Data
public class MiniappAccessResponse {
    private String secureUrl;
    private String sessionToken;
    private long expiresIn;
}
