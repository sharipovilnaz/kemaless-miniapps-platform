package com.kemaless.paymentminiapp.sso;

import jakarta.validation.constraints.NotBlank;

public record SsoExchangeRequest(@NotBlank String ticket, String realmCode, String miniAppCode) {}
