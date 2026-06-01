package com.kemaless.core.dto.request;

import com.kemaless.core.enums.AccessType;
import com.kemaless.core.enums.RoleMatchMode;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record AccessPolicyRequest(
        @NotNull AccessType accessType,
        RoleMatchMode roleMatchMode,
        Set<String> requiredRoles

){}
