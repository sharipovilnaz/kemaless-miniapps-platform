package com.kemaless.core.security;

import com.kemaless.core.auth.dto.AdminJwtPrincipal;
import com.kemaless.core.auth.dto.RealmClientJwtPrincipal;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.repository.PlatformUserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component("adminPermissionEvaluator")
@RequiredArgsConstructor
public class AdminPermissionEvaluator {

    private final PlatformUserRepository platformUserRepository;

    public boolean canCreateRealm(Authentication authentication) {
        return isRoot(authentication);
    }

    public boolean canListRealms(Authentication authentication) {
        return isRoot(authentication);
    }

    public boolean canViewRealm(Authentication authentication, String realmCode) {
        return isRoot(authentication) || isPlatformAdminInRealm(authentication, realmCode);
    }

    public boolean canManageRealm(Authentication authentication, String realmCode) {
        return isRoot(authentication) || isPlatformAdminInRealm(authentication, realmCode);
    }

    public boolean canManageRealmClients(Authentication authentication, String realmCode) {
        return canManageRealm(authentication, realmCode);
    }

    public boolean canManageRealmRoles(Authentication authentication, String realmCode) {
        return canManageRealm(authentication, realmCode);
    }

    public boolean canManageRoleMappings(Authentication authentication, String realmCode) {
        return canManageRealm(authentication, realmCode);
    }

    public boolean canManageRealmMiniApps(Authentication authentication, String realmCode) {
        return canManageRealm(authentication, realmCode);
    }

    public boolean canCreateUser(Authentication authentication, String requestRealmCode) {
        if (!isAuthenticated(authentication)) {
            return false;
        }
        if (!StringUtils.hasText(requestRealmCode)) {
            return isRoot(authentication);
        }
        return canManageUserInRealm(authentication, requestRealmCode);
    }

    public boolean canListUsers(Authentication authentication, String realmCode) {
        return isRoot(authentication) || isPlatformAdminInRealm(authentication, realmCode);
    }

    public boolean canManageUserInRealm(Authentication authentication, String realmCode) {
        return isRoot(authentication) || isPlatformAdminInRealm(authentication, realmCode);
    }

    public boolean canViewUser(Authentication authentication, UUID userId) {
        return isRoot(authentication) || isPlatformAdminInUserRealm(authentication, userId);
    }

    public boolean canUpdateUser(Authentication authentication, UUID userId) {
        return canViewUser(authentication, userId);
    }

    public boolean canChangeUserAccess(Authentication authentication, UUID userId, String targetRealmCode) {
        return isRoot(authentication)
                || (isPlatformAdminInUserRealm(authentication, userId)
                && isPlatformAdminInRealm(authentication, targetRealmCode));
    }

    public boolean canManageGlobalCatalog(Authentication authentication) {
        return isRoot(authentication); // Только ROOT добавляет миниаппы в платформу
    }


    public boolean canViewGlobalCatalog(Authentication authentication) {
        return isRoot(authentication) || isPlatformAdmin(authentication);
    }


    public boolean canViewRuntimeCatalog(Authentication authentication, String realmCode) {
        if (!isAuthenticated(authentication)) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof RealmClientJwtPrincipal hostPrincipal) {
            return StringUtils.hasText(realmCode) && realmCode.equals(hostPrincipal.realmCode());
        }
        return isRoot(authentication) || hasRealmAccess(authentication, realmCode);
    }

    public boolean canViewAudit(Authentication authentication, String realmCode) {
        if (!isAuthenticated(authentication)) return false;
        return isRoot(authentication) ||
                ((isPlatformAdmin(authentication) || isPlatformAuditor(authentication)) && hasRealmAccess(authentication, realmCode));
    }

    public boolean canViewStatistics(Authentication authentication, String realmCode) {
        return canViewAudit(authentication, realmCode);
    }

    public boolean canUseCopilot(Authentication authentication) {
        return isAuthenticated(authentication)
                && (isRoot(authentication) || isPlatformAdmin(authentication) || isPlatformAuditor(authentication));
    }

    private boolean isRoot(Authentication authentication) {
        return role(authentication) == GlobalRole.ROOT;
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return role(authentication) == GlobalRole.PLATFORM_ADMIN;
    }

    private boolean isPlatformAuditor(Authentication authentication) {
        return role(authentication) == GlobalRole.PLATFORM_AUDITOR;
    }

    private boolean isPlatformAdminInRealm(Authentication authentication, String realmCode) {
        return isPlatformAdmin(authentication) && hasRealmAccess(authentication, realmCode);
    }

    private boolean isPlatformAdminInUserRealm(Authentication authentication, UUID userId) {
        if (!isPlatformAdmin(authentication) || userId == null) {
            return false;
        }
        return platformUserRepository.findById(userId)
                .map(PlatformUser::getRealm)
                .filter(realm -> realm != null)
                .map(realm -> hasRealmAccess(authentication, realm.getCode()))
                .orElse(false);
    }

    private boolean hasRealmAccess(Authentication authentication, String realmCode) {
        AdminJwtPrincipal principal = principal(authentication);
        if (principal == null || !StringUtils.hasText(realmCode)) {
            return false;
        }
        return realmCode.equals(principal.realmCode());
    }

    private GlobalRole role(Authentication authentication) {
        AdminJwtPrincipal principal = principal(authentication);
        return principal != null ? principal.globalRole() : null;
    }

    private AdminJwtPrincipal principal(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AdminJwtPrincipal adminPrincipal ? adminPrincipal : null;
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated();
    }

    public boolean canManageMiniAppCatalog(Authentication authentication) {
        return isRoot(authentication);
    }

    public boolean canViewMiniAppCatalog(Authentication authentication) {
        return isRoot(authentication) || isPlatformAdmin(authentication);
    }
}
