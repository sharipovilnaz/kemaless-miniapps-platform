package com.kemaless.paymentminiapp.sso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsoUserInfo(String externalUserId, String email, List<String> realmRoles) {}
