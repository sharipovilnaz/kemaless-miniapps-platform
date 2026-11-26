package com.kemaless.miniappsdk.impl;

import com.kemaless.miniappsdk.api.dto.HostToken;
import com.kemaless.miniappsdk.api.dto.LaunchResult;
import com.kemaless.miniappsdk.api.dto.TokenIntrospectionResult;
import com.kemaless.miniappsdk.api.dto.UserContext;
import com.kemaless.miniappsdk.exception.RealmException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MiniAppPlatformClientImplTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger tokenCalls = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/api/realms/bank/auth/token", exchange -> {
            tokenCalls.incrementAndGet();
            respondJson(exchange, 200, """
                    {"accessToken":"host-jwt-1","expiresIn":900,"tokenType":"Bearer"}
                    """);
        });
        server.createContext("/api/realms/bank/miniapps/payment-service/launch", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (!"Bearer host-jwt-1".equals(auth)) {
                respondJson(exchange, 401, "{\"message\":\"Unauthorized\"}");
                return;
            }
            respondJson(exchange, 200, """
                    {"launchUrl":"http://localhost:8091/start?ticket=abc","ticketExpiresIn":60}
                    """);
        });
        server.createContext("/api/realms/bank/auth/introspect", exchange -> respondJson(exchange, 200, """
                {"active":true,"realmCode":"bank","miniAppCode":"payment-service","externalUserId":"u1","realmRoles":["USER"]}
                """));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void cachesHostTokenAndAppendsRealmAndMiniAppToLaunchUrlAndIntrospects() {
        MiniAppPlatformClientImpl client = MiniAppPlatformClientImpl.builder()
                .platformUrl(baseUrl)
                .realmCode("bank")
                .clientId("default-bank")
                .clientSecret("test-secret")
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build();

        HostToken first = client.getToken();
        HostToken second = client.getToken();
        assertThat(first.getAccessToken()).isEqualTo("host-jwt-1");
        assertThat(second.getAccessToken()).isEqualTo("host-jwt-1");
        assertThat(tokenCalls.get()).isEqualTo(1);

        LaunchResult launch = client.launchMiniApp(
                "payment-service",
                UserContext.builder().externalUserId("u1").roles(List.of("CARD_OWNER")).build()
        );
        assertThat(launch.getLaunchUrl())
                .contains("ticket=abc")
                .contains("realmCode=bank")
                .contains("miniAppCode=payment-service");

        TokenIntrospectionResult introspection = client.introspectToken("miniapp-jwt");
        assertThat(introspection.isActive()).isTrue();
        assertThat(introspection.getMiniAppCode()).isEqualTo("payment-service");
    }

    @Test
    void blankTokenIntrospectionReturnsInactiveWithoutHttpCall() {
        MiniAppPlatformClientImpl client = MiniAppPlatformClientImpl.builder()
                .platformUrl(baseUrl)
                .realmCode("bank")
                .clientId("default-bank")
                .clientSecret("test-secret")
                .build();

        TokenIntrospectionResult result = client.introspectToken("   ");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void mapsErrorStatusToRealmException() {
        server.createContext("/api/realms/bank/miniapps/missing/launch",
                exchange -> respondJson(exchange, 403, "{\"message\":\"Access denied\"}"));

        MiniAppPlatformClientImpl client = MiniAppPlatformClientImpl.builder()
                .platformUrl(baseUrl)
                .realmCode("bank")
                .clientId("default-bank")
                .clientSecret("test-secret")
                .build();

        assertThatThrownBy(() -> client.launchMiniApp(
                "missing",
                UserContext.builder().externalUserId("u1").build()))
                .isInstanceOf(RealmException.class)
                .hasMessageContaining("403");
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
