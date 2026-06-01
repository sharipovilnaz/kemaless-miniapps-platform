package com.kemaless.core.metric;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlatformMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordLaunchSuccess(String realmCode, String miniAppCode) {
        meterRegistry.counter("miniapp_launch_total",
                        "realm", realmCode,
                        "miniapp", miniAppCode,
                        "status", "success")
                .increment();
    }

    public void recordLaunchDenied(String realmCode, String miniAppCode, String reason) {
        meterRegistry.counter("miniapp_launch_denied_total",
                        "realm", realmCode,
                        "miniapp", miniAppCode,
                        "reason", reason)
                .increment();
    }

    public void recordSsoExchangeSuccess(String realmCode, String miniAppCode) {
        meterRegistry.counter("sso_exchange_total",
                        "realm", realmCode,
                        "miniapp", miniAppCode,
                        "status", "success")
                .increment();
    }

    public void recordSsoExchangeFailed(String realmCode, String miniAppCode, String error) {
        meterRegistry.counter("sso_exchange_failed_total",
                        "realm", realmCode,
                        "miniapp", miniAppCode,
                        "error", error)
                .increment();
    }


    public void recordHostTokenIssued(String realmCode) {
        meterRegistry.counter("auth_token_issued_total",
                        "realm", realmCode,
                        "type", "host_service_token")
                .increment();
    }
}