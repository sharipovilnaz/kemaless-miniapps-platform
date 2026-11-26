package com.kemaless.kemalessbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kemaless.kemalessbank.account.AccountController;
import com.kemaless.kemalessbank.account.AccountService;
import com.kemaless.kemalessbank.account.AccountStatus;
import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.auth.AuthController;
import com.kemaless.kemalessbank.auth.AuthService;
import com.kemaless.kemalessbank.common.ApiExceptionHandler;
import com.kemaless.kemalessbank.config.BankSecurityProperties;
import com.kemaless.kemalessbank.config.SecurityConfig;
import com.kemaless.kemalessbank.miniapp.MiniappController;
import com.kemaless.kemalessbank.miniapp.MiniappLaunchSessionResponse;
import com.kemaless.kemalessbank.miniapp.MiniappService;
import com.kemaless.kemalessbank.operation.OperationController;
import com.kemaless.kemalessbank.operation.OperationService;
import com.kemaless.kemalessbank.operation.TransferResponse;
import com.kemaless.kemalessbank.security.JwtAuthenticationFilter;
import com.kemaless.kemalessbank.security.JwtService;
import com.kemaless.kemalessbank.user.BankRole;
import com.kemaless.kemalessbank.user.BankUser;
import com.kemaless.kemalessbank.user.BankUserRepository;
import com.kemaless.kemalessbank.user.UserController;
import com.kemaless.kemalessbank.user.UserDeviceService;
import com.kemaless.kemalessbank.user.UserService;
import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import com.kemaless.miniappsdk.api.dto.TokenIntrospectionResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        AccountController.class,
        OperationController.class,
        UserController.class,
        MiniappController.class,
        AuthController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        ApiExceptionHandler.class
})
@EnableConfigurationProperties(BankSecurityProperties.class)
@TestPropertySource(properties = {
        "bank.jwt.issuer=kemaless-bank",
        "bank.jwt.secret=test-only-jwt-secret-key-at-least-32-bytes",
        "bank.jwt.access-token-ttl=PT30M",
        "bank.jwt.refresh-token-ttl=P14D",
        "bank.miniapp-api.allowed-codes=payment-service"
})
class BankSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MiniAppPlatformClient miniAppPlatformClient;

    @MockitoBean
    private BankUserRepository bankUserRepository;

    @MockitoBean
    private BankAccountRepository accountRepository;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private OperationService operationService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserDeviceService userDeviceService;

    @MockitoBean
    private MiniappService miniappService;

    @MockitoBean
    private AuthService authService;

    @Test
    void allowedMiniappTokenCanCallContractEndpointsOnly() throws Exception {
        BankUser alice = new BankUser(
                "alice",
                "hash",
                "Alice",
                "alice@example.com",
                "+79001112233",
                Set.of(new BankRole("USER", "User")));
        when(userService.requireByUsername("alice")).thenReturn(alice);
        when(accountRepository.sumActiveBalancesByCurrency(alice)).thenReturn(Map.of("RUB", new BigDecimal("100.00")));
        when(accountService.findUserAccounts("alice")).thenReturn(List.of());
        when(operationService.history("alice")).thenReturn(List.of());
        when(operationService.transferToAnotherUser(eq("alice"), any()))
                .thenReturn(new TransferResponse(null, null, List.of()));

        when(miniAppPlatformClient.introspectToken("miniapp-token"))
                .thenReturn(TokenIntrospectionResult.builder()
                        .active(true)
                        .type("MINIAPP_USER_TOKEN")
                        .realmCode("bank")
                        .miniAppCode("payment-service")
                        .externalUserId("alice")
                        .realmRoles(List.of("USER"))
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/operations").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/transfers/peer")
                        .header("Authorization", "Bearer miniapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"recipient\":\"bob\",\"amount\":10.00}"))
                .andExpect(status().isOk());

        // Запрещённые для miniapp эндпоинты возвращают 403 Forbidden.
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer miniapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New\",\"currency\":\"RUB\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/accounts/1/close").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/accounts/1/block").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/accounts/1/unblock").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/accounts/1/statement").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/transfers/internal")
                        .header("Authorization", "Bearer miniapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":10.00}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer miniapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hacker\",\"email\":\"h@example.com\",\"phone\":\"\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer miniapp-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"12345678\",\"newPassword\":\"87654321\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/me/devices").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/miniapps/payment-service/launch").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer miniapp-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void disallowedMiniappAndWrongTypeAreRejectedWith401() throws Exception {
        when(miniAppPlatformClient.introspectToken("disallowed-app"))
                .thenReturn(TokenIntrospectionResult.builder()
                        .active(true)
                        .type("MINIAPP_USER_TOKEN")
                        .miniAppCode("unknown-app")
                        .externalUserId("alice")
                        .build());
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer disallowed-app"))
                .andExpect(status().isUnauthorized());

        when(miniAppPlatformClient.introspectToken("wrong-type"))
                .thenReturn(TokenIntrospectionResult.builder()
                        .active(true)
                        .type("HOST_SERVICE_TOKEN")
                        .miniAppCode("payment-service")
                        .externalUserId("alice")
                        .build());
        mockMvc.perform(get("/api/accounts").header("Authorization", "Bearer wrong-type"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bankUserJwtCanCallPrivilegedBankEndpointsAndValidationRejectsInvalidAmounts() throws Exception {
        BankUser alice = new BankUser(
                "alice",
                "hash",
                "Alice",
                "alice@example.com",
                "+79001112233",
                Set.of(new BankRole("USER", "User")));
        String accessToken = jwtService.createAccessToken(alice).token();

        BankAccount opened = new BankAccount(alice, "KML-1", "Main", "RUB", BigDecimal.ZERO);
        when(accountService.openAccount(eq("alice"), any())).thenReturn(opened);
        when(accountService.blockAccount("alice", 1L)).thenReturn(new BankAccount(alice, "KML-1", "Main", "RUB", BigDecimal.ZERO));
        when(miniappService.launch(eq("alice"), eq("payment-service"), any()))
                .thenReturn(new MiniappLaunchSessionResponse("http://localhost:8091/start?ticket=t1", 60));

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Reserve\",\"currency\":\"RUB\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/accounts/1/block").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(AccountStatus.ACTIVE.name()));
        mockMvc.perform(post("/api/miniapps/payment-service/launch").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Проверка @Digits / @DecimalMax / @DecimalMin на суммах перевода.
        mockMvc.perform(post("/api/transfers/peer")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"recipient\":\"bob\",\"amount\":10.005}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/transfers/internal")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":1000000000000000.00}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseExceptionDoesNotLeakInternalTableNames() throws Exception {
        BankUser alice = new BankUser(
                "alice",
                "hash",
                "Alice",
                "alice@example.com",
                "+79001112233",
                Set.of(new BankRole("USER", "User")));
        String accessToken = jwtService.createAccessToken(alice).token();

        when(userDeviceService.listDevices(eq("alice"), any()))
                .thenThrow(new DataAccessResourceFailureException("ERROR: relation \"bank_user_device\" does not exist"));

        String body = mockMvc.perform(get("/api/users/me/devices").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body).doesNotContain("bank_user_device").doesNotContain("Liquibase");
    }
}
