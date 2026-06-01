package com.kemaless.paymentminiapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentMiniappApplicationTests {

    static final MockWebServer PLATFORM = new MockWebServer();
    static final MockWebServer BANK = new MockWebServer();

    static {
        try {
            PLATFORM.start();
            BANK.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void platformAndBankUrls(DynamicPropertyRegistry registry) {
        registry.add("miniapp.realm-code", () -> "bank-mobile");
        registry.add("miniapp.default-mini-app-code", () -> "payment-service");
        registry.add("miniapp.platform.base-url", () -> "http://127.0.0.1:" + PLATFORM.getPort());
        registry.add("miniapp.host-api.base-url", () -> "http://127.0.0.1:" + BANK.getPort());
    }

    @AfterAll
    static void shutdown() throws IOException {
        PLATFORM.shutdown();
        BANK.shutdown();
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void drainMocks() throws Exception {
        while (BANK.takeRequest(1, TimeUnit.MILLISECONDS) != null) {}
        while (PLATFORM.takeRequest(1, TimeUnit.MILLISECONDS) != null) {}
    }

    @Test
    void contextLoads() {
        assertThat(rest.getForEntity("/api/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void realTicketCallsPlatformExchange() throws Exception {
        String platformJson =
                """
                {
                  "accessToken": "m-at",
                  "refreshToken": "m-rt",
                  "expiresIn": 900,
                  "user": { "externalUserId": "u1", "email": "u1@mail.com", "realmRoles": ["USER"] },
                  "realm": { "code": "bank-mobile" },
                  "miniApp": { "code": "payment-service" }
                }
                """;
        PLATFORM.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(platformJson));

        ResponseEntity<String> res = rest.postForEntity(
                "/api/session/exchange",
                java.util.Map.of("ticket", "one-time-abc", "miniAppCode", "payment-service"),
                String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = readTree(res.getBody());
        assertThat(root.path("accessToken").asText()).isEqualTo("m-at");
        assertThat(PLATFORM.takeRequest().getPath()).isEqualTo("/api/realms/bank-mobile/sso/exchange");
    }

    @Test
    void exchangeUsesRealmCodeFromLaunchUrlWhenProvided() throws Exception {
        PLATFORM.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "accessToken": "m-at",
                          "refreshToken": "m-rt",
                          "expiresIn": 900,
                          "user": { "externalUserId": "u1", "email": "u1@mail.com", "realmRoles": ["USER"] },
                          "realm": { "code": "bank-kemaless" },
                          "miniApp": { "code": "payment-service" }
                        }
                        """));

        ResponseEntity<String> res = rest.postForEntity(
                "/api/session/exchange",
                Map.of("ticket", "one-time-abc", "realmCode", "bank-kemaless", "miniAppCode", "payment-service"),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(PLATFORM.takeRequest().getPath()).isEqualTo("/api/realms/bank-kemaless/sso/exchange");
    }

    @Test
    void manifestExposesAdminPanelRegistrationContract() {
        ResponseEntity<String> res = rest.getForEntity("/api/miniapp/manifest", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = readTree(res.getBody());
        assertThat(root.path("code").asText()).isEqualTo("payment-service");
        assertThat(root.has("status")).isFalse();
        assertThat(root.path("launch").path("url").asText()).isEqualTo("/start");
        assertThat(root.path("sso").path("exchangeEndpoint").asText()).isEqualTo("/api/session/exchange");
        assertThat(root.path("launch").has("optionalQueryParams")).isFalse();
        assertThat(root.path("capabilities")).anySatisfy(node ->
                assertThat(node.asText()).isEqualTo("payments.peer-transfer"));
    }

    @Test
    void paymentAccountsProxiesToBankWithAuthorization() throws Exception {
        BANK.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"id\":7,\"title\":\"Main\",\"status\":\"ACTIVE\"}]"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("bank-jwt-xyz");
        ResponseEntity<String> res = rest.exchange(
                "/api/payment/accounts", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(BANK.takeRequest().getHeader("Authorization")).isEqualTo("Bearer bank-jwt-xyz");
        JsonNode arr = readTree(res.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.get(0).path("id").asInt()).isEqualTo(7);
    }

    @Test
    void peerTransferProxiesValidatedPaymentCommandToBank() throws Exception {
        BANK.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"operations\":[{\"id\":1}],\"toAccount\":{\"id\":9,\"currency\":\"RUB\"}}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("miniapp-bank-api-token");
        ResponseEntity<String> res = rest.exchange(
                "/api/payment/transfers/peer",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fromAccountId", 7,
                        "recipient", "alice",
                        "amount", 125.75,
                        "description", "test payment",
                        "toAccountNumber", "40817810000000000001"), headers),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        RecordedRequest request = BANK.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/transfers/peer");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer miniapp-bank-api-token");
        JsonNode body = readTree(request.getBody().readUtf8());
        assertThat(body.path("fromAccountId").asLong()).isEqualTo(7);
        assertThat(body.path("recipient").asText()).isEqualTo("alice");
        assertThat(body.path("amount").asText()).isEqualTo("125.75");
        assertThat(body.path("description").asText()).isEqualTo("test payment");
        assertThat(body.path("toAccountNumber").asText()).isEqualTo("40817810000000000001");
    }

    @Test
    void peerTransferValidationErrorDoesNotCallBank() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("miniapp-bank-api-token");
        ResponseEntity<String> res = rest.exchange(
                "/api/payment/transfers/peer",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "fromAccountId", 7,
                        "recipient", "a",
                        "amount", 0), headers),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(takeNoBankRequest()).isNull();
    }

    @Test
    void upstreamBankProblemDetailIsPreservedForMiniappFrontend() {
        BANK.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"title\":\"Некорректный запрос\",\"detail\":\"Недостаточно средств\"}"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("miniapp-bank-api-token");
        ResponseEntity<String> res = rest.exchange(
                "/api/payment/accounts", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(readTree(res.getBody()).path("detail").asText()).isEqualTo("Недостаточно средств");
    }

    @Test
    void paymentAccountsWithoutTokenReturns401() {
        ResponseEntity<String> res = rest.getForEntity("/api/payment/accounts", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(takeNoBankRequest()).isNull();
    }

    private RecordedRequest takeNoBankRequest() {
        try {
            return BANK.takeRequest(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
