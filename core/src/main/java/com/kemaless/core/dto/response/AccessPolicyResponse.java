package com.kemaless.core.dto.response;

import com.kemaless.core.enums.AccessType;
import com.kemaless.core.enums.RoleMatchMode;
import lombok.Builder;

import java.util.Set;

@Builder
public record AccessPolicyResponse(
        AccessType accessType,
        RoleMatchMode roleMatchMode,
        Set<String> requiredRoles
) {}
