package com.kemaless.paymentminiapp.sso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsoSessionResponse(
        String accessToken,
        String refreshToken,
        Integer expiresIn,
        SsoUserInfo user,
        SsoRealmInfo realm,
        SsoMiniAppInfo miniApp) {}
