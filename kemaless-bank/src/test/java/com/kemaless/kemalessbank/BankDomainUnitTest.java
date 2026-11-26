package com.kemaless.kemalessbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.auth.AuthService;
import com.kemaless.kemalessbank.auth.RegisterRequest;
import com.kemaless.kemalessbank.common.BadRequestException;
import com.kemaless.kemalessbank.config.BankSecurityProperties;
import com.kemaless.kemalessbank.operation.InternalTransferRequest;
import com.kemaless.kemalessbank.operation.OperationRepository;
import com.kemaless.kemalessbank.operation.OperationService;
import com.kemaless.kemalessbank.security.JwtService;
import com.kemaless.kemalessbank.user.BankRole;
import com.kemaless.kemalessbank.user.BankRoleRepository;
import com.kemaless.kemalessbank.user.BankUser;
import com.kemaless.kemalessbank.user.BankUserRepository;
import com.kemaless.kemalessbank.user.UserDeviceService;
import com.kemaless.kemalessbank.user.UserDto;
import com.kemaless.kemalessbank.user.UserService;
import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

class BankDomainUnitTest {

    @Test
    void miniappApiAllowlistIsFailClosed() {
        var empty = new BankSecurityProperties.MiniappApi(List.of());
        assertThat(empty.allows("payment-service")).isFalse();
        assertThat(empty.allows("")).isFalse();
        assertThat(empty.allows(null)).isFalse();

        var allowlist = new BankSecurityProperties.MiniappApi(List.of("payment-service"));
        assertThat(allowlist.allows("payment-service")).isTrue();
        assertThat(allowlist.allows(" payment-service ")).isTrue();
        assertThat(allowlist.allows("other-app")).isFalse();
    }

    @Test
    void internalTransferLocksAccountsInAscendingIdOrderAndNormalizesScale() throws Exception {
        UserService userService = mock(UserService.class);
        BankUserRepository userRepository = mock(BankUserRepository.class);
        BankAccountRepository accountRepository = mock(BankAccountRepository.class);
        OperationRepository operationRepository = mock(OperationRepository.class);

        OperationService operationService = new OperationService(
                userService, userRepository, accountRepository, operationRepository);

        BankUser user = new BankUser("alice", "hash", "Alice", Set.of(new BankRole("USER", "User")));
        setId(user, 10L);
        BankAccount acc2 = new BankAccount(user, "KML-2", "To", "RUB", new BigDecimal("50.00"));
        setId(acc2, 2L);
        BankAccount acc5 = new BankAccount(user, "KML-5", "From", "RUB", new BigDecimal("100.00"));
        setId(acc5, 5L);

        when(userService.requireByUsername("alice")).thenReturn(user);
        when(accountRepository.findCurrencyByIdAndUser(5L, user)).thenReturn(Optional.of("RUB"));
        when(accountRepository.findCurrencyByIdAndUser(2L, user)).thenReturn(Optional.of("RUB"));
        when(accountRepository.findAllByIdInOrderByIdAscForUpdate(List.of(2L, 5L))).thenReturn(List.of(acc2, acc5));
        when(operationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = operationService.transferBetweenOwnAccounts(
                "alice", new InternalTransferRequest(5L, 2L, new BigDecimal("25.5"), "test"));

        verify(accountRepository).findAllByIdInOrderByIdAscForUpdate(List.of(2L, 5L));
        assertThat(response.fromAccount().balance()).isEqualByComparingTo("74.50");
        assertThat(response.toAccount().balance()).isEqualByComparingTo("75.50");

        assertThatThrownBy(() -> operationService.transferBetweenOwnAccounts(
                        "alice", new InternalTransferRequest(5L, 2L, new BigDecimal("1.005"), "bad scale")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> operationService.transferBetweenOwnAccounts(
                        "alice", new InternalTransferRequest(5L, 2L, new BigDecimal("1000000000000000.00"), "max")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void authServiceHandlesPlatformLogoutFailureAndAccountNumberCollision() {
        BankUserRepository userRepository = mock(BankUserRepository.class);
        BankRoleRepository roleRepository = mock(BankRoleRepository.class);
        BankAccountRepository accountRepository = mock(BankAccountRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserDeviceService deviceService = mock(UserDeviceService.class);
        MiniAppPlatformClient platformClient = mock(MiniAppPlatformClient.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<MiniAppPlatformClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(platformClient);

        JwtService jwtService = new JwtService(new BankSecurityProperties(
                new BankSecurityProperties.Jwt(
                        "kemaless-bank",
                        "test-only-jwt-secret-key-at-least-32-bytes",
                        Duration.ofMinutes(30),
                        Duration.ofDays(14)),
                new BankSecurityProperties.Cors(List.of(), List.of()),
                new BankSecurityProperties.Csp("'self'"),
                new BankSecurityProperties.MiniappApi(List.of("payment-service")),
                "http://localhost:5173"));

        AuthService authService = new AuthService(
                userRepository, roleRepository, accountRepository, passwordEncoder, jwtService, provider, deviceService);

        doThrow(new RuntimeException("platform down")).when(platformClient).logoutUser("alice");
        authService.notifyPlatformLogout("alice");

        when(userRepository.existsByUsername("user.1")).thenReturn(false);
        when(userRepository.existsByEmail("u1@example.com")).thenReturn(false);
        when(roleRepository.findByCodeIgnoreCase("USER")).thenReturn(Optional.of(new BankRole("USER", "User")));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.existsByAccountNumber("KML-USER1-0001")).thenReturn(true);
        when(accountRepository.existsByAccountNumber(any())).thenAnswer(inv ->
                "KML-USER1-0001".equals(inv.getArgument(0)));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(
                new RegisterRequest("user.1", "password123", "User One", "u1@example.com", ""), null);

        ArgumentCaptor<BankAccount> accountCaptor = ArgumentCaptor.forClass(BankAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getAccountNumber())
                .startsWith("KML-")
                .isNotEqualTo("KML-USER1-0001");
    }

    @Test
    void userDtoDoesNotMixForeignCurrenciesIntoRubBalance() {
        BankUser user = new BankUser("alice", "hash", "Alice", Set.of(new BankRole("USER", "User")));
        UserDto dto = UserDto.from(user, Map.of("EUR", new BigDecimal("250.00"), "RUB", new BigDecimal("100.00")));
        assertThat(dto.balance()).isEqualByComparingTo("100.00");
        assertThat(dto.currency()).isEqualTo("RUB");
        assertThat(dto.balancesByCurrency()).containsEntry("EUR", new BigDecimal("250.00"));
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
