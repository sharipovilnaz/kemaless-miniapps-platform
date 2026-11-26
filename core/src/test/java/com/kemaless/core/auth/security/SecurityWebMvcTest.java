package com.kemaless.core.auth.security;

import com.kemaless.core.ai.config.AiAvailabilityRegistry;
import com.kemaless.core.ai.controller.AdminCopilotController;
import com.kemaless.core.ai.service.AdminCopilotMediaService;
import com.kemaless.core.ai.service.AdminCopilotServiceImpl;
import com.kemaless.core.auth.config.SecurityConfig;
import com.kemaless.core.auth.controller.MiniAppAuthController;
import com.kemaless.core.auth.controller.MiniAppLaunchController;
import com.kemaless.core.auth.controller.MiniAppSessionController;
import com.kemaless.core.auth.controller.SsoController;
import com.kemaless.core.auth.controller.handler.GlobalExceptionHandler;
import com.kemaless.core.auth.dto.api.LaunchMiniAppResponse;
import com.kemaless.core.auth.dto.api.SessionLogoutResponse;
import com.kemaless.core.auth.dto.api.SsoExchangeResponse;
import com.kemaless.core.auth.dto.api.TokenIntrospectionResponse;
import com.kemaless.core.auth.entity.PlatformUser;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import com.kemaless.core.auth.repository.PlatformUserRepository;
import com.kemaless.core.auth.service.AdminJwtService;
import com.kemaless.core.auth.service.HostServiceJwtService;
import com.kemaless.core.auth.service.LaunchMiniAppService;
import com.kemaless.core.auth.service.MiniAppSessionService;
import com.kemaless.core.auth.service.RealmClientTokenValidationService;
import com.kemaless.core.auth.service.SsoExchangeService;
import com.kemaless.core.auth.service.TokenIntrospectionService;
import com.kemaless.core.controller.RealmController;
import com.kemaless.core.enums.ClientStatus;
import com.kemaless.core.model.Realm;
import com.kemaless.core.model.RealmClient;
import com.kemaless.core.security.AdminPermissionEvaluator;
import com.kemaless.core.service.interfaces.RealmService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        MiniAppLaunchController.class,
        MiniAppSessionController.class,
        SsoController.class,
        MiniAppAuthController.class,
        RealmController.class,
        AdminCopilotController.class
})
@Import({
        SecurityConfig.class,
        AdminJwtAuthenticationFilter.class,
        RealmClientJwtAuthenticationFilter.class,
        AdminJwtService.class,
        HostServiceJwtService.class,
        AdminPermissionEvaluator.class,
        GlobalExceptionHandler.class
})
class SecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminJwtService adminJwtService;
    @Autowired
    private HostServiceJwtService hostServiceJwtService;

    @MockitoBean
    private RealmClientTokenValidationService realmClientTokenValidationService;
    @MockitoBean
    private LaunchMiniAppService launchMiniAppService;
    @MockitoBean
    private MiniAppSessionService miniAppSessionService;
    @MockitoBean
    private SsoExchangeService ssoExchangeService;
    @MockitoBean
    private TokenIntrospectionService tokenIntrospectionService;
    @MockitoBean
    private RealmService realmService;
    @MockitoBean
    private PlatformUserRepository platformUserRepository;
    @MockitoBean
    private AdminCopilotServiceImpl adminCopilotService;
    @MockitoBean
    private AdminCopilotMediaService adminCopilotMediaService;
    @MockitoBean
    private AiAvailabilityRegistry aiAvailabilityRegistry;

    @Test
    void launchAndLogoutRequireMatchingRealmHostServiceToken() throws Exception {
        Realm realm = Realm.builder().code("bank").name("Bank").build();
        RealmClient client = RealmClient.builder().realm(realm).clientId("default-bank").status(ClientStatus.ACTIVE).build();
        String hostToken = hostServiceJwtService.createAccessToken(client);
        String adminToken = adminJwtService.createAccessToken(
                PlatformUser.builder()
                        .id(UUID.randomUUID())
                        .username("root")
                        .passwordHash("hash")
                        .status(PlatformUserStatus.ACTIVE)
                        .globalRole(GlobalRole.ROOT)
                        .build(),
                null
        );

        doNothing().when(realmClientTokenValidationService).validateActiveClient(any());
        when(launchMiniAppService.launch(eq("bank"), eq("payment-service"), any(), any()))
                .thenReturn(new LaunchMiniAppResponse("http://localhost:8091/start?ticket=abc", 60));
        when(miniAppSessionService.logoutByHost(eq("bank"), any()))
                .thenReturn(new SessionLogoutResponse(2));

        // Без токена доступ запрещён
        mockMvc.perform(post("/api/realms/bank/miniapps/payment-service/launch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":{\"externalUserId\":\"u1\"}}"))
                .andExpect(status().isForbidden());

        // Токен от другого realm отклоняется фильтром с 401
        mockMvc.perform(post("/api/realms/other-realm/miniapps/payment-service/launch")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":{\"externalUserId\":\"u1\"}}"))
                .andExpect(status().isUnauthorized());

        // Админский токен не принимается на B2B launch endpoint
        mockMvc.perform(post("/api/realms/bank/miniapps/payment-service/launch")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":{\"externalUserId\":\"u1\"}}"))
                .andExpect(status().isUnauthorized());

        // Валидный host service token для своего realm допускается
        mockMvc.perform(post("/api/realms/bank/miniapps/payment-service/launch")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user\":{\"externalUserId\":\"u1\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.launchUrl").value("http://localhost:8091/start?ticket=abc"));

        mockMvc.perform(post("/api/realms/bank/sessions/logout")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalUserId\":\"u1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(2));
    }

    @Test
    void adminRbacReturns403ForUnauthorizedRoleAndPermitsPublicSsoEndpoints() throws Exception {
        String realmAdminToken = adminJwtService.createAccessToken(
                PlatformUser.builder()
                        .id(UUID.randomUUID())
                        .username("realm_admin")
                        .passwordHash("hash")
                        .status(PlatformUserStatus.ACTIVE)
                        .globalRole(GlobalRole.PLATFORM_ADMIN)
                        .build(),
                "bank"
        );

        // Отказ @PreAuthorize должен возвращать 403 Forbidden, а не 500
        mockMvc.perform(get("/api/admin/realms")
                        .header("Authorization", "Bearer " + realmAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("AccessDenied"));

        // B2B токен не допускается к Admin Copilot
        Realm realm = Realm.builder().code("bank").name("Bank").build();
        RealmClient client = RealmClient.builder().realm(realm).clientId("default-bank").status(ClientStatus.ACTIVE).build();
        String hostToken = hostServiceJwtService.createAccessToken(client);
        mockMvc.perform(post("/api/admin/copilot/chat")
                        .header("Authorization", "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + UUID.randomUUID() + "\",\"text\":\"status\"}"))
                .andExpect(status().isUnauthorized());

        // Публичные SSO-эндпоинты работают без токена
        when(ssoExchangeService.exchange(eq("bank"), any())).thenReturn(SsoExchangeResponse.bearer(
                "at", "rt", 900,
                new SsoExchangeResponse.User("u1", "u1@example.com", List.of("USER"), Map.of()),
                new SsoExchangeResponse.Realm("bank"),
                new SsoExchangeResponse.MiniApp("payment-service"),
                Map.of()
        ));
        when(tokenIntrospectionService.introspect("bank", "at")).thenReturn(
                TokenIntrospectionResponse.active("bank", "payment-service", "u1", List.of("USER"), Instant.now().plusSeconds(60))
        );

        mockMvc.perform(post("/api/realms/bank/sso/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"t1\",\"miniAppCode\":\"payment-service\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("at"));

        mockMvc.perform(post("/api/realms/bank/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"at\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
