package com.kemaless.kemalessbank;

import static org.assertj.core.api.Assertions.assertThat;

import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.config.BankSecurityProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KemalessBankApplicationTests {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    static final MockWebServer PLATFORM = new MockWebServer();

    static {
        POSTGRES.start();
        try {
            PLATFORM.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void registerTestInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("bank.jwt.secret", () -> "test-only-jwt-secret-key-at-least-32-bytes");
        registry.add("bank.miniapp-api.allowed-codes", () -> "payment-service");
        registry.add("miniapp.platform.enabled", () -> "true");
        registry.add("miniapp.platform.url", () -> "http://127.0.0.1:" + PLATFORM.getPort());
        registry.add("miniapp.platform.realm-code", () -> "bank-mobile");
        registry.add("miniapp.platform.client-id", () -> "bank-mobile-backend");
        registry.add("miniapp.platform.client-secret", () -> "test-secret");
    }

    @AfterAll
    static void shutdownInfrastructure() throws IOException {
        PLATFORM.shutdown();
        POSTGRES.stop();
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private BankAccountRepository accountRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void emptyMiniappApiAllowListRejectsMiniappTokens() {
        BankSecurityProperties.MiniappApi emptyAllowList = new BankSecurityProperties.MiniappApi(List.of());
        assertThat(emptyAllowList.allows("payment-service")).isFalse();
        assertThat(emptyAllowList.allows(null)).isFalse();

        BankSecurityProperties.MiniappApi configured = new BankSecurityProperties.MiniappApi(List.of("payment-service"));
        assertThat(configured.allows("payment-service")).isTrue();
        assertThat(configured.allows("other-miniapp")).isFalse();
    }

    @Test
    void authenticatedBankAndMiniappFlowWorks() {
        PLATFORM.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if ("/api/realms/bank-mobile/auth/token".equals(path)) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"accessToken\":\"host-jwt\",\"expiresIn\":3600,\"tokenType\":\"Bearer\"}");
                }
                if ("/api/realms/bank-mobile/miniapps/catalog".equals(path)) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("[{\"code\":\"payment-service\",\"name\":\"Payment Service\",\"description\":\"Payments\",\"iconUrl\":null,\"entryUrl\":\"http://localhost:3001/start\",\"metadata\":{\"category\":\"finance\"}}]");
                }
                if ("/api/realms/bank-mobile/miniapps/payment-service/launch".equals(path)) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"launchUrl\":\"http://localhost:3001/start?ticket=ticket-1\",\"ticketExpiresIn\":60}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        String username = "flow" + Instant.now().toEpochMilli();
        Map<String, Object> register = post("/api/auth/register", Map.of(
                "username", username,
                "password", "password123",
                "fullName", "Flow User",
                "email", username + "@example.com",
                "phone", "+7 900 100-20-30"));
        String accessToken = (String) register.get("accessToken");
        assertThat(accessToken).isNotBlank();

        HttpHeaders headers = bearer(accessToken);
        List<Map<String, Object>> accounts = getList("/api/accounts", headers);
        assertThat(accounts).isNotEmpty();

        List<Map<String, Object>> miniapps = getList("/api/miniapps", headers);
        assertThat(miniapps).anySatisfy(miniapp -> assertThat(miniapp.get("id")).isEqualTo("payment-service"));

        Map<String, Object> launch = post("/api/miniapps/payment-service/launch", Map.of(), headers);
        assertThat(((Number) launch.get("ticketExpiresIn")).intValue()).isEqualTo(60);
        assertThat((String) launch.get("launchUrl")).contains("ticket=ticket-1");
    }

    @Test
    void registrationProfileEditingAndPasswordChangeWork() {
        String suffix = String.valueOf(Instant.now().toEpochMilli());
        String username = "user" + suffix;
        String oldPassword = "password123";
        String newPassword = "password456";
        String userAgent = "KemalessBankApplicationTests/1.0";

        HttpHeaders registerHeaders = new HttpHeaders();
        registerHeaders.setContentType(MediaType.APPLICATION_JSON);
        registerHeaders.set("User-Agent", userAgent);
        Map<String, Object> register = post("/api/auth/register", Map.of(
                "username", username,
                "password", oldPassword,
                "fullName", "Новый Клиент",
                "email", username + "@example.com",
                "phone", "+7 900 111-22-33"), registerHeaders);
        HttpHeaders headers = bearer((String) register.get("accessToken"));
        headers.set("User-Agent", userAgent);

        Map<String, Object> profile = put("/api/users/me", Map.of(
                "fullName", "Обновленный Клиент",
                "email", username + ".updated@example.com",
                "phone", "+7 900 444-55-66"), headers);
        assertThat(profile.get("fullName")).isEqualTo("Обновленный Клиент");
        assertThat(profile.get("email")).isEqualTo(username + ".updated@example.com");

        put("/api/users/me/password", Map.of(
                "currentPassword", oldPassword,
                "newPassword", newPassword), headers);

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("User-Agent", userAgent);
        Map<String, Object> login = post("/api/auth/login", Map.of(
                "username", username,
                "password", newPassword), loginHeaders);
        assertThat((String) login.get("accessToken")).isNotBlank();

        HttpHeaders deviceHeaders = bearer((String) login.get("accessToken"));
        deviceHeaders.set("User-Agent", userAgent);
        List<Map<String, Object>> devices = getList("/api/users/me/devices", deviceHeaders);
        assertThat(devices).isNotEmpty();
        assertThat(devices.stream().anyMatch(d -> Boolean.TRUE.equals(d.get("currentDevice")))).isTrue();
    }

    @Test
    void accountCanBeOpenedAndClosed() {
        String username = "account" + Instant.now().toEpochMilli();
        Map<String, Object> register = post("/api/auth/register", Map.of(
                "username", username,
                "password", "password123",
                "fullName", "Account User",
                "email", username + "@example.com",
                "phone", "+7 900 200-30-40"));
        HttpHeaders headers = bearer((String) register.get("accessToken"));

        Map<String, Object> opened = post("/api/accounts", Map.of(
                "title", "Savings",
                "currency", "RUB"), headers);
        assertThat(opened.get("status")).isEqualTo("ACTIVE");
        assertThat(opened.get("title")).isEqualTo("Savings");

        Map<String, Object> closed = post("/api/accounts/" + opened.get("id") + "/close", Map.of(), headers);
        assertThat(closed.get("status")).isEqualTo("CLOSED");
    }

    @Test
    void peerTransferByUsernameAndPhoneResolvesDefaultAccount() {
        String suffix = String.valueOf(Instant.now().toEpochMilli());
        String bob = "bob" + suffix;
        String ann = "ann" + suffix;
        String tail = suffix.substring(Math.max(0, suffix.length() - 9));
        String bobPhone = "+79" + tail;
        String annPhone = "+78" + tail;
        Map<String, Object> bobReg = post("/api/auth/register", Map.of(
                "username", bob,
                "password", "password123",
                "fullName", "Bob",
                "email", bob + "@example.com",
                "phone", bobPhone));
        post("/api/auth/register", Map.of(
                "username", ann,
                "password", "password123",
                "fullName", "Ann",
                "email", ann + "@example.com",
                "phone", annPhone));
        HttpHeaders bobHeaders = bearer((String) bobReg.get("accessToken"));
        List<Map<String, Object>> bobAccounts = getList("/api/accounts", bobHeaders);
        Long fromId = ((Number) bobAccounts.getFirst().get("id")).longValue();
        accountRepository.findById(fromId).ifPresent(a -> {
            a.credit(new BigDecimal("500.00"));
            accountRepository.save(a);
        });

        Map<String, Object> byLogin = post("/api/transfers/peer", Map.of(
                "fromAccountId", fromId,
                "recipient", ann,
                "amount", 100.50,
                "description", "Перевод по логину"), bobHeaders);
        assertThat(byLogin.get("operations")).isNotNull();

        post("/api/transfers/peer", Map.of(
                "fromAccountId", fromId,
                "recipient", annPhone,
                "amount", 50.00,
                "description", "Перевод по телефону"), bobHeaders);

        List<Map<String, Object>> ops = getList("/api/operations", bobHeaders);
        assertThat(ops.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void internalTransferOperationsStatementAndBlockingWork() {
        String username = "ops" + Instant.now().toEpochMilli();
        Map<String, Object> register = post("/api/auth/register", Map.of(
                "username", username,
                "password", "password123",
                "fullName", "Operations User",
                "email", username + "@example.com",
                "phone", "+7 900 300-40-50"));
        HttpHeaders headers = bearer((String) register.get("accessToken"));
        List<Map<String, Object>> initialAccounts = getList("/api/accounts", headers);
        Long sourceAccountId = ((Number) initialAccounts.getFirst().get("id")).longValue();

        BankAccount sourceAccount = accountRepository.findById(sourceAccountId).orElseThrow();
        sourceAccount.credit(new BigDecimal("1000.00"));
        accountRepository.save(sourceAccount);

        Map<String, Object> target = post("/api/accounts", Map.of(
                "title", "Reserve",
                "currency", "RUB"), headers);
        Long targetAccountId = ((Number) target.get("id")).longValue();

        Map<String, Object> transfer = post("/api/transfers/internal", Map.of(
                "fromAccountId", sourceAccountId,
                "toAccountId", targetAccountId,
                "amount", 250.00,
                "description", "Test transfer"), headers);
        assertThat((List<?>) transfer.get("operations")).hasSize(2);

        List<Map<String, Object>> operations = getList("/api/operations", headers);
        assertThat(operations).hasSize(2);

        Map<String, Object> statement = getMap("/api/accounts/" + sourceAccountId + "/statement", headers);
        assertThat((List<?>) statement.get("operations")).hasSize(1);

        Map<String, Object> blocked = post("/api/accounts/" + targetAccountId + "/block", Map.of(), headers);
        assertThat(blocked.get("status")).isEqualTo("BLOCKED");

        Map<String, Object> unblocked = post("/api/accounts/" + targetAccountId + "/unblock", Map.of(), headers);
        assertThat(unblocked.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void internalTransferRejectsDifferentCurrencies() {
        String username = "cur" + Instant.now().toEpochMilli();
        Map<String, Object> register = post("/api/auth/register", Map.of(
                "username", username,
                "password", "password123",
                "fullName", "Currency User",
                "email", username + "@example.com",
                "phone", "+7 900 400-50-60"));
        HttpHeaders headers = bearer((String) register.get("accessToken"));
        List<Map<String, Object>> initial = getList("/api/accounts", headers);
        Long rubId = ((Number) initial.getFirst().get("id")).longValue();
        Map<String, Object> eurAcc = post("/api/accounts", Map.of("title", "Euro", "currency", "EUR"), headers);
        Long eurId = ((Number) eurAcc.get("id")).longValue();
        accountRepository.findById(rubId).ifPresent(a -> {
            a.credit(new BigDecimal("100.00"));
            accountRepository.save(a);
        });
        ResponseEntity<String> response = rest.exchange(
                "/api/transfers/internal",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("fromAccountId", rubId, "toAccountId", eurId, "amount", 10.00), headers),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void peerTransferCreditsRecipientAccountInSenderCurrencyNotJustFirstById() {
        String suffix = String.valueOf(Instant.now().toEpochMilli());
        String bob = "bobcur" + suffix;
        String ann = "anncur" + suffix;
        Map<String, Object> bobReg = post("/api/auth/register", Map.of(
                "username", bob,
                "password", "password123",
                "fullName", "Bob",
                "email", bob + "@example.com",
                "phone", "+79" + suffix.substring(Math.max(0, suffix.length() - 9))));
        HttpHeaders bobHeaders = bearer((String) bobReg.get("accessToken"));
        Map<String, Object> annReg = post("/api/auth/register", Map.of(
                "username", ann,
                "password", "password123",
                "fullName", "Ann",
                "email", ann + "@example.com",
                "phone", "+78" + suffix.substring(Math.max(0, suffix.length() - 9))));
        HttpHeaders annHeaders = bearer((String) annReg.get("accessToken"));

        post("/api/accounts", Map.of("title", "Euro jar", "currency", "EUR"), annHeaders);
        List<Map<String, Object>> annAccountsAfterEur = getList("/api/accounts", annHeaders);
        Long annFirstRubId = annAccountsAfterEur.stream()
                .filter(a -> "RUB".equals(a.get("currency")))
                .findFirst()
                .map(a -> ((Number) a.get("id")).longValue())
                .orElseThrow();
        post("/api/accounts/" + annFirstRubId + "/close", Map.of(), annHeaders);
        Map<String, Object> annNewRub = post("/api/accounts", Map.of("title", "RUB again", "currency", "RUB"), annHeaders);
        Long expectedRubId = ((Number) annNewRub.get("id")).longValue();

        List<Map<String, Object>> bobAccounts = getList("/api/accounts", bobHeaders);
        Long bobRubId = ((Number) bobAccounts.getFirst().get("id")).longValue();
        accountRepository.findById(bobRubId).ifPresent(a -> {
            a.credit(new BigDecimal("500.00"));
            accountRepository.save(a);
        });

        Map<String, Object> transfer = post("/api/transfers/peer", Map.of(
                "fromAccountId", bobRubId,
                "recipient", ann,
                "amount", 25.00,
                "description", "order test"), bobHeaders);
        Map<String, Object> toAccount = (Map<String, Object>) transfer.get("toAccount");
        assertThat(toAccount.get("currency")).isEqualTo("RUB");
        assertThat(((Number) toAccount.get("id")).longValue()).isEqualTo(expectedRubId);
    }

    @Test
    void concurrentInternalTransfersPreventDoubleSpending() throws Exception {
        String username = "conc" + Instant.now().toEpochMilli();
        Map<String, Object> register = post("/api/auth/register", Map.of(
                "username", username,
                "password", "password123",
                "fullName", "Concurrent User",
                "email", username + "@example.com",
                "phone", "+7 900 500-60-70"));
        HttpHeaders headers = bearer((String) register.get("accessToken"));
        List<Map<String, Object>> initial = getList("/api/accounts", headers);
        Long fromId = ((Number) initial.getFirst().get("id")).longValue();
        BankAccount fromAccount = accountRepository.findById(fromId).orElseThrow();
        fromAccount.credit(new BigDecimal("150.00"));
        accountRepository.save(fromAccount);

        Map<String, Object> target = post("/api/accounts", Map.of("title", "Target", "currency", "RUB"), headers);
        Long toId = ((Number) target.get("id")).longValue();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<ResponseEntity<String>> attempt = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return rest.exchange(
                        "/api/transfers/internal",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("fromAccountId", fromId, "toAccountId", toId, "amount", 100.00), headers),
                        String.class);
            };
            Future<ResponseEntity<String>> first = pool.submit(attempt);
            Future<ResponseEntity<String>> second = pool.submit(attempt);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpStatus> statuses = new ArrayList<>();
            statuses.add(HttpStatus.valueOf(first.get(10, TimeUnit.SECONDS).getStatusCode().value()));
            statuses.add(HttpStatus.valueOf(second.get(10, TimeUnit.SECONDS).getStatusCode().value()));

            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.BAD_REQUEST);
        } finally {
            pool.shutdownNow();
        }

        BankAccount updatedFrom = accountRepository.findById(fromId).orElseThrow();
        BankAccount updatedTo = accountRepository.findById(toId).orElseThrow();
        assertThat(updatedFrom.getBalance()).isEqualByComparingTo("50.00");
        assertThat(updatedTo.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void miniappTokenAccessIsRestrictedToAllowedMiniappAndEndpoints() {
        String username = "mapp" + Instant.now().toEpochMilli();
        post("/api/auth/register", Map.of(
                "username", username,
                "password", "password123",
                "fullName", "Miniapp Scope User",
                "email", username + "@example.com",
                "phone", "+7 900 700-80-90"));

        PLATFORM.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/api/realms/bank-mobile/auth/introspect".equals(request.getPath())) {
                    String body = request.getBody().readUtf8();
                    String miniAppCode = body.contains("miniapp-token-other") ? "other-miniapp" : "payment-service";
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    {"active":true,"type":"MINIAPP_USER_TOKEN","realmCode":"bank-mobile","miniAppCode":"%s","externalUserId":"%s","realmRoles":["USER"]}
                                    """.formatted(miniAppCode, username));
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        HttpHeaders otherHeaders = bearer("miniapp-token-other");
        ResponseEntity<String> rejected = rest.exchange(
                "/api/accounts", HttpMethod.GET, new HttpEntity<>(otherHeaders), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders allowedHeaders = bearer("miniapp-token-allowed");
        ResponseEntity<String> allowedAccounts = rest.exchange(
                "/api/accounts", HttpMethod.GET, new HttpEntity<>(allowedHeaders), String.class);
        assertThat(allowedAccounts.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> forbiddenCreateAccount = rest.exchange(
                "/api/accounts",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Blocked", "currency", "RUB"), allowedHeaders),
                String.class);
        assertThat(forbiddenCreateAccount.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void miniappP2pTransferEnforcesOwnershipTypeAndRealm() {
        String suffix = String.valueOf(Instant.now().toEpochMilli());
        String alice = "p2palice" + suffix;
        String bob = "p2pbob" + suffix;
        String carol = "p2pcarol" + suffix;

        Map<String, Object> aliceReg = post("/api/auth/register", Map.of(
                "username", alice,
                "password", "password123",
                "fullName", "Alice P2P",
                "email", alice + "@example.com",
                "phone", "+7911" + suffix.substring(Math.max(0, suffix.length() - 7))));
        Map<String, Object> bobReg = post("/api/auth/register", Map.of(
                "username", bob,
                "password", "password123",
                "fullName", "Bob P2P",
                "email", bob + "@example.com",
                "phone", "+7922" + suffix.substring(Math.max(0, suffix.length() - 7))));
        Map<String, Object> carolReg = post("/api/auth/register", Map.of(
                "username", carol,
                "password", "password123",
                "fullName", "Carol P2P",
                "email", carol + "@example.com",
                "phone", "+7933" + suffix.substring(Math.max(0, suffix.length() - 7))));

        Long aliceAccountId = ((Number) getList("/api/accounts", bearer((String) aliceReg.get("accessToken")))
                .getFirst().get("id")).longValue();
        Long bobAccountId = ((Number) getList("/api/accounts", bearer((String) bobReg.get("accessToken")))
                .getFirst().get("id")).longValue();
        String carolAccountNumber = (String) getList("/api/accounts", bearer((String) carolReg.get("accessToken")))
                .getFirst().get("accountNumber");

        BankAccount aliceAcc = accountRepository.findById(aliceAccountId).orElseThrow();
        aliceAcc.credit(new BigDecimal("300.00"));
        accountRepository.save(aliceAcc);
        BankAccount bobAcc = accountRepository.findById(bobAccountId).orElseThrow();
        bobAcc.credit(new BigDecimal("200.00"));
        accountRepository.save(bobAcc);

        PLATFORM.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/api/realms/bank-mobile/auth/introspect".equals(request.getPath())) {
                    String body = request.getBody().readUtf8();
                    String json;
                    if (body.contains("token-missing-type")) {
                        json = """
                                {"active":true,"realmCode":"bank-mobile","miniAppCode":"payment-service","externalUserId":"%s","realmRoles":["USER"]}
                                """.formatted(alice);
                    } else if (body.contains("token-wrong-realm")) {
                        json = """
                                {"active":true,"type":"MINIAPP_USER_TOKEN","realmCode":"foreign-realm","miniAppCode":"payment-service","externalUserId":"%s","realmRoles":["USER"]}
                                """.formatted(alice);
                    } else {
                        json = """
                                {"active":true,"type":"MINIAPP_USER_TOKEN","realmCode":"bank-mobile","miniAppCode":"payment-service","externalUserId":"%s","realmRoles":["USER"]}
                                """.formatted(alice);
                    }
                    return new MockResponse().setHeader("Content-Type", "application/json").setBody(json);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        ResponseEntity<String> missingType = rest.exchange(
                "/api/transfers/peer",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("fromAccountId", aliceAccountId, "recipient", bob, "amount", 50.00),
                        bearer("token-missing-type")),
                String.class);
        assertThat(missingType.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> wrongRealm = rest.exchange(
                "/api/transfers/peer",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("fromAccountId", aliceAccountId, "recipient", bob, "amount", 50.00),
                        bearer("token-wrong-realm")),
                String.class);
        assertThat(wrongRealm.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders validMiniapp = bearer("token-valid-alice");
        // Чужой fromAccountId при P2P через miniapp-токен отклоняется без изменения баланса жертвы.
        ResponseEntity<String> foreignSource = rest.exchange(
                "/api/transfers/peer",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("fromAccountId", bobAccountId, "recipient", alice, "amount", 50.00),
                        validMiniapp),
                String.class);
        assertThat(foreignSource.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(accountRepository.findById(bobAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("200.00");

        // Перевод на чужой toAccountNumber не проходит проверку владельца.
        ResponseEntity<String> mismatchedTarget = rest.exchange(
                "/api/transfers/peer",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of(
                                "fromAccountId", aliceAccountId,
                                "recipient", bob,
                                "amount", 50.00,
                                "toAccountNumber", carolAccountNumber),
                        validMiniapp),
                String.class);
        assertThat(mismatchedTarget.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> validPeer = rest.exchange(
                "/api/transfers/peer",
                HttpMethod.POST,
                new HttpEntity<>(
                        Map.of("fromAccountId", aliceAccountId, "recipient", bob, "amount", 50.00),
                        validMiniapp),
                String.class);
        assertThat(validPeer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accountRepository.findById(aliceAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("250.00");
        assertThat(accountRepository.findById(bobAccountId).orElseThrow().getBalance())
                .isEqualByComparingTo("250.00");
    }

    @Test
    void liquibaseCleanInstallAndPrePrSchemaUpgradePreserveExistingData() throws Exception {
        String adminUrl = POSTGRES.getJdbcUrl();
        try (var adminConn = java.sql.DriverManager.getConnection(adminUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             var stmt = adminConn.createStatement()) {
            stmt.execute("CREATE DATABASE liquibase_clean_test");
            stmt.execute("CREATE DATABASE liquibase_upgrade_test");
        }

        String cleanUrl = "jdbc:postgresql://%s:%d/liquibase_clean_test"
                .formatted(POSTGRES.getHost(), POSTGRES.getFirstMappedPort());
        runLiquibaseUpdate(cleanUrl, "db/changelog/db.changelog-master.yaml");

        String upgradeUrl = "jdbc:postgresql://%s:%d/liquibase_upgrade_test"
                .formatted(POSTGRES.getHost(), POSTGRES.getFirstMappedPort());
        runLiquibaseUpdate(upgradeUrl, "db/base-initial-schema.yaml");
        try (var conn = java.sql.DriverManager.getConnection(upgradeUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             var stmt = conn.createStatement()) {
            var versionBefore = conn.getMetaData().getColumns(null, "public", "bank_account", "version");
            assertThat(versionBefore.next()).isFalse();

            stmt.executeUpdate("""
                    INSERT INTO bank_user (username, password_hash, full_name, created_at, updated_at)
                    VALUES ('legacy_user', 'hash', 'Legacy User', NOW(), NOW())
                    """);
            stmt.executeUpdate("""
                    INSERT INTO bank_account (user_id, account_number, title, currency, balance, status, created_at)
                    VALUES ((SELECT id FROM bank_user WHERE username = 'legacy_user'), 'KML-LEGACY-1', 'Legacy', 'RUB', 420.50, 'ACTIVE', NOW())
                    """);
        }

        runLiquibaseUpdate(upgradeUrl, "db/changelog/db.changelog-master.yaml");
        runLiquibaseUpdate(upgradeUrl, "db/changelog/db.changelog-master.yaml");

        try (var conn = java.sql.DriverManager.getConnection(upgradeUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT version, balance FROM bank_account WHERE account_number = 'KML-LEGACY-1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("version")).isZero();
            assertThat(rs.getBigDecimal("balance")).isEqualByComparingTo("420.50");
        }
    }

    private static void runLiquibaseUpdate(String jdbcUrl, String changelogPath) throws Exception {
        try (var conn = java.sql.DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            var database = liquibase.database.DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(conn));
            try (var liquibase = new liquibase.Liquibase(
                    changelogPath,
                    new liquibase.resource.ClassLoaderResourceAccessor(),
                    database)) {
                liquibase.update(new liquibase.Contexts(), new liquibase.LabelExpression());
            }
        }
    }

    private Map<String, Object> post(String path, Object body) {
        return post(path, body, new HttpHeaders());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> put(String path, Object body, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, headers), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), List.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
