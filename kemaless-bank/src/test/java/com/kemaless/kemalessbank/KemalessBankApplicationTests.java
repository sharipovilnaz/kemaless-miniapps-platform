package com.kemaless.kemalessbank;

import static org.assertj.core.api.Assertions.assertThat;

import com.kemaless.kemalessbank.account.BankAccount;
import com.kemaless.kemalessbank.account.BankAccountRepository;
import com.kemaless.kemalessbank.config.BankSecurityProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
        registry.add("bank.jwt.secret", () -> "2FTQErHPMDI66u335zxKWaVAohNwNZehJ6xM5qP9WKO");
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
    void emptyMiniappApiAllowListTrustsActivePlatformToken() {
        BankSecurityProperties.MiniappApi miniappApi = new BankSecurityProperties.MiniappApi(List.of());

        assertThat(miniappApi.allows("segsrrgsfd")).isTrue();
        assertThat(miniappApi.allows(null)).isFalse();
    }

    @Test
    void authenticatedBankAndMiniappFlowWorks() {
        PLATFORM.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"accessToken\":\"host-jwt\",\"expiresIn\":3600,\"tokenType\":\"Bearer\"}"));
        PLATFORM.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                        "[{\"code\":\"payment-service\",\"name\":\"Payment Service\",\"description\":\"Payments\",\"iconUrl\":null,\"entryUrl\":\"http://localhost:3001/start\",\"metadata\":{\"category\":\"finance\"}}]"));
        PLATFORM.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                        "{\"launchUrl\":\"http://localhost:3001/start?ticket=ticket-1\",\"ticketExpiresIn\":60}"));

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
