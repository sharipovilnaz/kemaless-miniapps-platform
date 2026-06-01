package com.kemaless.miniappsdk.impl;

import com.kemaless.miniappsdk.api.MiniAppPlatformClient;
import com.kemaless.miniappsdk.api.dto.HostToken;
import com.kemaless.miniappsdk.api.dto.LaunchResult;
import com.kemaless.miniappsdk.api.dto.MiniAppInfo;
import com.kemaless.miniappsdk.api.dto.MiniAppLaunchRequest;
import com.kemaless.miniappsdk.api.dto.TokenIntrospectionResult;
import com.kemaless.miniappsdk.api.dto.UserContext;
import com.kemaless.miniappsdk.api.dto.response.HostTokenResponse;
import com.kemaless.miniappsdk.exception.RealmException;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
public class MiniAppPlatformClientImpl implements MiniAppPlatformClient {

    private final String realmCode;
    private final String clientId;
    private final String clientSecret;
    private final RestClient restClient;
    private final int catalogSafeRetryMaxAttempts;

    private String cacheToken;
    private Instant tokenExpirationTime;

    @Builder
    public MiniAppPlatformClientImpl(String platformUrl,
                                     String realmCode,
                                     String clientId,
                                     String clientSecret,
                                     Duration connectTimeout,
                                     Duration readTimeout,
                                     Integer catalogSafeRetryMaxAttempts) {
        this.realmCode = realmCode;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        Duration c = Objects.requireNonNullElse(connectTimeout, Duration.ofSeconds(5));
        Duration r = Objects.requireNonNullElse(readTimeout, Duration.ofSeconds(15));
        this.catalogSafeRetryMaxAttempts = Objects.requireNonNullElse(catalogSafeRetryMaxAttempts, 3);
        this.restClient = RestClient.builder()
                .baseUrl(platformUrl)
                .requestFactory(createRequestFactory(c, r))
                .requestInterceptor(observabilityInterceptor())
                .build();

        log.info("Initialized MiniAppPlatformClient for realm: {}", realmCode);
    }

    private static ClientHttpRequestFactory createRequestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(timeoutToMillisBounded(connectTimeout));
        rf.setReadTimeout(timeoutToMillisBounded(readTimeout));
        return rf;
    }

    private static int timeoutToMillisBounded(Duration d) {
        long ms = d.toMillis();
        if (ms <= 0) {
            ms = Duration.ofMillis(1).toMillis();
        }
        if (ms > Integer.MAX_VALUE) {
            ms = Integer.MAX_VALUE;
        }
        return Math.toIntExact(ms);
    }

    @Override
    public synchronized HostToken getToken() {
        if (cacheToken != null && Instant.now().plusSeconds(10).isBefore(tokenExpirationTime)) {
            log.debug("Using cached host service token for realm: {}. Expires in {} seconds",
                    realmCode, ChronoUnit.SECONDS.between(Instant.now(), tokenExpirationTime));

            long remainingSeconds = ChronoUnit.SECONDS.between(Instant.now(), tokenExpirationTime);
            return HostToken.builder()
                    .accessToken(cacheToken)
                    .expiresIn(remainingSeconds)
                    .tokenType("Bearer")
                    .build();
        }

        log.info("Requesting new host service token for realm: {}", realmCode);

        HostTokenResponse response = restClient.post()
                .uri("/api/realms/{realmCode}/auth/token", realmCode)
                .body(Map.of("clientId", clientId, "clientSecret", clientSecret))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw platformException("Host authorization failed", res);
                })
                .body(HostTokenResponse.class);

        if (response == null || response.getAccessToken() == null) {
            throw new RealmException("Platform returned an empty token response", HttpStatus.UNAUTHORIZED);
        }

        this.cacheToken = response.getAccessToken();
        this.tokenExpirationTime = Instant.now().plusSeconds(response.getExpiresIn());

        log.debug("Successfully obtained and cached new token (TTL {} s)", response.getExpiresIn());

        return HostToken.builder()
                .accessToken(cacheToken)
                .expiresIn(response.getExpiresIn())
                .tokenType("Bearer")
                .build();
    }

    @Override
    public List<MiniAppInfo> getCatalog() {
        log.debug("Fetching mini-app catalog for realm: {}", realmCode);

        int attempts = Math.max(1, catalogSafeRetryMaxAttempts);
        RealmException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return fetchCatalogInternal();
            } catch (RealmException ex) {
                lastError = ex;
                HttpStatus status = ex.getStatus();
                if (!isRetrySafeForCatalog(status)) {
                    throw ex;
                }
                if (attempt >= attempts) {
                    throw ex;
                }
                backoffBeforeRetry(attempt);
                log.warn("Retrying catalog GET (realm {}), attempt {}/{} after status {}",
                        realmCode, attempt + 1, attempts, status);
            }
        }
        throw Objects.requireNonNullElseGet(lastError,
                () -> new RealmException("Failed to fetch catalog after retries"));
    }

    private List<MiniAppInfo> fetchCatalogInternal() {
        return restClient.get()
                .uri("/api/realms/{realmCode}/miniapps/catalog", realmCode)
                .header("Authorization", "Bearer " + getToken().getAccessToken())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw platformException("Failed to fetch catalog", res);
                })
                .body(new ParameterizedTypeReference<List<MiniAppInfo>>() {
                });
    }

    private static boolean isRetrySafeForCatalog(HttpStatus status) {
        if (status == null) {
            return false;
        }
        return status.isSameCodeAs(HttpStatus.REQUEST_TIMEOUT)
                || status == HttpStatus.TOO_MANY_REQUESTS
                || status == HttpStatus.BAD_GATEWAY
                || status == HttpStatus.SERVICE_UNAVAILABLE
                || status == HttpStatus.GATEWAY_TIMEOUT;
    }

    private static void backoffBeforeRetry(int attempt) {
        try {
            long ms = Math.min(100L * (1L << (attempt - 1)), 2000L);
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RealmException("Interrupted while waiting to retry catalog request");
        }
    }

    @Override
    public LaunchResult launchMiniApp(String miniAppCode, MiniAppLaunchRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.getUser(), "request.user");
        log.info("Attempting to launch mini-app '{}' for user '{}' in realm '{}'",
                miniAppCode, request.getUser().getExternalUserId(), realmCode);

        LaunchResult launch = restClient.post()
                .uri("/api/realms/{realmCode}/miniapps/{miniAppCode}/launch", realmCode, miniAppCode)
                .header("Authorization", "Bearer " + getToken().getAccessToken())
                .body(buildLaunchBody(request))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw platformException("Failed to launch mini-app", res);
                })
                .body(LaunchResult.class);

        if (launch == null || launch.getLaunchUrl() == null) {
            throw new RealmException("Platform returned an empty launch response");
        }
        launch.setLaunchUrl(appendLaunchContext(launch.getLaunchUrl(), miniAppCode));
        return launch;
    }

    private static Map<String, Object> buildLaunchBody(MiniAppLaunchRequest request) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("user", buildUserBody(request.getUser()));
        if (request.getDevice() != null) {
            body.put("device", request.getDevice());
        }
        if (request.getContext() != null && !request.getContext().isEmpty()) {
            body.put("context", request.getContext());
        }
        return body;
    }

    private static Map<String, Object> buildUserBody(UserContext user) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("externalUserId", user.getExternalUserId());
        body.put("email", user.getEmail());
        body.put("roles", user.getRoles() == null ? List.of() : user.getRoles());
        body.put("attributes", user.getAttributes() == null ? Map.of() : user.getAttributes());
        return body;
    }

    private String appendLaunchContext(String launchUrl, String miniAppCode) {
        return UriComponentsBuilder.fromUriString(launchUrl)
                .replaceQueryParam("realmCode", realmCode)
                .replaceQueryParam("miniAppCode", miniAppCode)
                .build()
                .toUriString();
    }

    @Override
    public void logoutUser(String externalUserId) {
        log.info("Logging out user '{}' from realm '{}'", externalUserId, realmCode);

        restClient.post()
                .uri("/api/realms/{realmCode}/sessions/logout", realmCode)
                .header("Authorization", "Bearer " + getToken().getAccessToken())
                .body(Map.of("externalUserId", externalUserId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw platformException("Failed to logout user", res);
                })
                .toBodilessEntity();
    }

    @Override
    public TokenIntrospectionResult introspectToken(String token) {
        if (token == null || token.isBlank()) {
            return TokenIntrospectionResult.builder().active(false).build();
        }

        TokenIntrospectionResult result = restClient.post()
                .uri("/api/realms/{realmCode}/auth/introspect", realmCode)
                .body(Map.of("token", token))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw platformException("Failed to introspect mini-app token", res);
                })
                .body(TokenIntrospectionResult.class);

        return result == null ? TokenIntrospectionResult.builder().active(false).build() : result;
    }

    @Override
    public void trackEvent(String miniAppCode, String eventName, Map<String, Object> payload) {
        log.debug("Tracking event '{}' for mini-app '{}' in realm '{}'", eventName, miniAppCode, realmCode);

        restClient.post()
                .uri("/api/realms/{realmCode}/miniapps/{miniAppCode}/events", realmCode, miniAppCode)
                .header("Authorization", "Bearer " + getToken().getAccessToken())
                .body(Map.of("eventName", eventName, "payload", payload))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw platformException("Failed to track event", res);
                })
                .toBodilessEntity();
    }

    private static RealmException platformException(String operation, ClientHttpResponse response) throws IOException {
        HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        String details = body.isBlank() ? "" : ". Response: " + truncate(body, 1000);
        return new RealmException(operation + ". Status: " + response.getStatusCode() + details, status);
    }

    private static String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    private ClientHttpRequestInterceptor observabilityInterceptor() {
        return (req, body, execution) -> {
            String requestId = UUID.randomUUID().toString();
            req.getHeaders().add("X-Request-ID", requestId);

            log.info("[RequestId: {}][realm:{}] {} {}", requestId, realmCode, req.getMethod(), req.getURI());

            ClientHttpResponse response = execution.execute(req, body);

            log.info("[RequestId: {}][realm:{}] status {}", requestId, realmCode, response.getStatusCode());

            return response;
        };
    }
}
