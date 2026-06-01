package com.kemaless.core.auth.dto;

import com.kemaless.core.auth.enums.AccessDecisionStatus;
import com.kemaless.core.auth.enums.AuthErrorCode;

public record AccessDecision(
        AccessDecisionStatus status,
        AuthErrorCode reason
) {
}
