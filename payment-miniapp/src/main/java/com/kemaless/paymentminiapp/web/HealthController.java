package com.kemaless.paymentminiapp.web;

import com.kemaless.paymentminiapp.config.MiniappPlatformProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final MiniappPlatformProperties properties;

    public HealthController(MiniappPlatformProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "payment-miniapp",
                "miniAppCode", properties.defaultMiniAppCode(),
                "checkedAt", Instant.now().toString());
    }

    @GetMapping({"/api/miniapp/manifest", "/.well-known/miniapp.json"})
    public Map<String, Object> manifest() {
        return Map.ofEntries(
                Map.entry("code", properties.defaultMiniAppCode()),
                Map.entry("name", "Платежный сервис"),
                Map.entry("description", "Переводы клиентам и просмотр операций внутри host-приложения."),
                Map.entry("version", "1.0.0"),
                Map.entry("launch", Map.of(
                        "url", "/start",
                        "mode", "iframe",
                        "requiredQueryParams", List.of("ticket"))),
                Map.entry("sso", Map.of(
                        "type", "ticket-exchange",
                        "exchangeEndpoint", "/api/session/exchange",
                        "ticketTtlSeconds", 60)),
                Map.entry("capabilities", List.of("payments.peer-transfer", "accounts.read", "operations.read")),
                Map.entry("permissions", List.of("host.accounts.read", "host.operations.read", "host.transfers.peer.write")),
                Map.entry("endpoints", Map.of(
                        "health", "/api/health",
                        "manifest", "/api/miniapp/manifest",
                        "sessionExchange", "/api/session/exchange")),
                Map.entry("embedding", Map.of(
                        "frameAncestors", List.of("configured by host deployment"),
                        "sandbox", "allow-scripts allow-forms allow-same-origin allow-popups allow-downloads")));
    }
}
