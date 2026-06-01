package com.kemaless.paymentminiapp.sso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsoMiniAppInfo(String code) {}
