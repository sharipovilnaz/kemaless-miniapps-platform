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
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmMiniApp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessControlServiceTest {

    private final AccessControlService accessControlService = new AccessControlService();

    @Test
    void deniesDisabledRealmAndUnpublishedMiniApp() {
        Realm disabledRealm = Realm.builder().code("bank").status(RealmStatus.DISABLED).build();
        RealmMiniApp miniApp = RealmMiniApp.builder()
                .realm(disabledRealm)
                .status(RealmMiniAppStatus.PUBLISHED)
                .build();
        AccessPolicy policy = AccessPolicy.builder().accessType(AccessType.PUBLIC_IN_REALM).build();
        AccessContext user = new AccessContext("u1", "u1@example.com", List.of("USER"), Map.of());

        AccessDecision realmDisabled = accessControlService.evaluate(miniApp, policy, user);
        assertThat(realmDisabled.status()).isEqualTo(AccessDecisionStatus.DENY);
        assertThat(realmDisabled.reason()).isEqualTo(AuthErrorCode.REALM_DISABLED);

        disabledRealm.setStatus(RealmStatus.ACTIVE);
        miniApp.setStatus(RealmMiniAppStatus.STOPPED);
        AccessDecision notPublished = accessControlService.evaluate(miniApp, policy, user);
        assertThat(notPublished.status()).isEqualTo(AccessDecisionStatus.DENY);
        assertThat(notPublished.reason()).isEqualTo(AuthErrorCode.MINIAPP_NOT_PUBLISHED);
    }

    @Test
    void enforcesAuthenticatedAndRoleBasedAnyAndAllPolicies() {
        Realm activeRealm = Realm.builder().code("bank").status(RealmStatus.ACTIVE).build();
        RealmMiniApp miniApp = RealmMiniApp.builder()
                .realm(activeRealm)
                .status(RealmMiniAppStatus.PUBLISHED)
                .build();

        AccessPolicy authPolicy = AccessPolicy.builder().accessType(AccessType.AUTHENTICATED).build();
        AccessContext anonymous = new AccessContext(null, null, List.of(), Map.of());
        AccessContext authenticated = new AccessContext("u1", null, List.of("USER"), Map.of());

        assertThat(accessControlService.evaluate(miniApp, authPolicy, anonymous).reason())
                .isEqualTo(AuthErrorCode.USER_NOT_AUTHENTICATED);
        assertThat(accessControlService.evaluate(miniApp, authPolicy, authenticated).status())
                .isEqualTo(AccessDecisionStatus.ALLOW);

        AccessPolicy anyRolePolicy = AccessPolicy.builder()
                .accessType(AccessType.ROLE_BASED)
                .roleMatchMode(RoleMatchMode.ANY)
                .requiredRoles(Set.of("PREMIUM", "VIP"))
                .build();
        assertThat(accessControlService.evaluate(miniApp, anyRolePolicy, authenticated).reason())
                .isEqualTo(AuthErrorCode.REQUIRED_ROLE_MISSING);
        assertThat(accessControlService.evaluate(
                miniApp,
                anyRolePolicy,
                new AccessContext("u1", null, List.of("USER", "VIP"), Map.of())).status())
                .isEqualTo(AccessDecisionStatus.ALLOW);

        AccessPolicy allRolePolicy = AccessPolicy.builder()
                .accessType(AccessType.ROLE_BASED)
                .roleMatchMode(RoleMatchMode.ALL)
                .requiredRoles(Set.of("USER", "VIP"))
                .build();
        assertThat(accessControlService.evaluate(
                miniApp,
                allRolePolicy,
                new AccessContext("u1", null, List.of("USER"), Map.of())).reason())
                .isEqualTo(AuthErrorCode.REQUIRED_ROLE_MISSING);
        assertThat(accessControlService.evaluate(
                miniApp,
                allRolePolicy,
                new AccessContext("u1", null, List.of("USER", "VIP"), Map.of())).status())
                .isEqualTo(AccessDecisionStatus.ALLOW);
    }
}
