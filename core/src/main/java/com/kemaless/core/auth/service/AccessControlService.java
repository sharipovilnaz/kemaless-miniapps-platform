package com.kemaless.core.auth.service;

import com.kemaless.core.auth.dto.AccessContext;
import com.kemaless.core.auth.dto.AccessDecision;
import com.kemaless.core.auth.enums.AccessDecisionStatus;
import com.kemaless.core.auth.enums.AuthErrorCode;
import com.kemaless.core.enums.AccessType;
import com.kemaless.core.enums.RealmMiniAppStatus;
import com.kemaless.core.enums.RealmStatus;
import com.kemaless.core.enums.RoleMatchMode;
import com.kemaless.core.model.AccessPolicy;
import com.kemaless.core.model.RealmMiniApp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AccessControlService {

    public AccessDecision evaluate(RealmMiniApp miniApp, AccessPolicy policy, AccessContext accessContext) {
        if (miniApp.getRealm().getStatus() != RealmStatus.ACTIVE) {
            return deny(AuthErrorCode.REALM_DISABLED);
        }
        if (miniApp.getStatus() != RealmMiniAppStatus.PUBLISHED) {
            return deny(AuthErrorCode.MINIAPP_NOT_PUBLISHED);
        }
        if (policy == null) {
            return deny(AuthErrorCode.POLICY_NOT_CONFIGURED);
        }
        AccessType accessType = policy.getAccessType();
        if (accessType == AccessType.PUBLIC_IN_REALM) {
            return allow();
        }
        if (!StringUtils.hasText(accessContext.externalUserId())) {
            return deny(AuthErrorCode.USER_NOT_AUTHENTICATED);
        }
        if (accessType == AccessType.AUTHENTICATED) {
            return allow();
        }
        if (accessType == AccessType.ROLE_BASED) {
            if (policy.getRoleMatchMode() == null) {
                return deny(AuthErrorCode.POLICY_NOT_CONFIGURED);
            }
            return evaluateRoles(policy, accessContext.realmRoles());
        }
        return deny(AuthErrorCode.POLICY_NOT_CONFIGURED);
    }

    private AccessDecision evaluateRoles(AccessPolicy policy, List<String> realmRoles) {
        Set<String> requiredRoles = safeSet(policy.getRequiredRoles());
        if (requiredRoles.isEmpty()) {
            return deny(AuthErrorCode.POLICY_NOT_CONFIGURED);
        }
        List<String> userRoles = safeList(realmRoles);
        boolean allowed = policy.getRoleMatchMode() == RoleMatchMode.ALL
                ? new HashSet<>(userRoles).containsAll(requiredRoles)
                : requiredRoles.stream().anyMatch(userRoles::contains);
        return allowed ? allow() : deny(AuthErrorCode.REQUIRED_ROLE_MISSING);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> Set<T> safeSet(Set<T> values) {
        return values == null ? Set.of() : values;
    }

    private AccessDecision allow() {
        return new AccessDecision(AccessDecisionStatus.ALLOW, null);
    }

    private AccessDecision deny(AuthErrorCode reason) {
        return new AccessDecision(AccessDecisionStatus.DENY, reason);
    }
}
