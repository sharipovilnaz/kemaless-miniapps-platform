package com.kemaless.core.service.impl;

import com.kemaless.core.audit.domain.AuditAction;
import com.kemaless.core.audit.repository.AuditEventRepository;
import com.kemaless.core.audit.repository.AuditMiniAppMetricsProjection;
import com.kemaless.core.auth.exception.ServiceException;
import com.kemaless.core.dto.response.RealmMiniAppMetricsResponse;
import com.kemaless.core.model.RealmMiniApp;
import com.kemaless.core.repository.RealmMiniAppRepository;
import com.kemaless.core.repository.RealmRepository;
import com.kemaless.core.service.interfaces.RealmMiniAppMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RealmMiniAppMetricsServiceImpl implements RealmMiniAppMetricsService {

    private static final long CACHE_TTL_SECONDS = 5L;
    private static final int DEFAULT_WINDOW_SECONDS = 300;
    private static final int MIN_WINDOW_SECONDS = 10;
    private static final int MAX_WINDOW_SECONDS = 86400;

    private final RealmRepository realmRepository;
    private final RealmMiniAppRepository realmMiniAppRepository;
    private final AuditEventRepository auditEventRepository;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public List<RealmMiniAppMetricsResponse> getRealmMetrics(String realmCode, Integer windowSeconds) {
        ensureRealmExists(realmCode);
        int normalizedWindowSeconds = normalizeWindowSeconds(windowSeconds);
        String cacheKey = "realm:" + realmCode + ":" + normalizedWindowSeconds;
        CacheEntry cached = cache.get(cacheKey);
        long nowEpochSecond = Instant.now().getEpochSecond();
        if (cached != null && cached.expiresAtEpochSecond > nowEpochSecond) {
            return cached.items;
        }

        Instant fromTime = Instant.now().minusSeconds(normalizedWindowSeconds);
        List<RealmMiniApp> realmMiniApps = realmMiniAppRepository.findAllActiveByRealmCode(realmCode);
        Map<String, RealmMiniAppMetricsAccumulator> metricsByMiniApp = new HashMap<>();
        for (RealmMiniApp realmMiniApp : realmMiniApps) {
            metricsByMiniApp.put(realmMiniApp.getMiniApp().getCode(), RealmMiniAppMetricsAccumulator.from(realmMiniApp));
        }

        List<AuditMiniAppMetricsProjection> projections =
                auditEventRepository.aggregateRealmMiniAppMetrics(realmCode, fromTime);
        projections.forEach(projection -> {
            RealmMiniAppMetricsAccumulator accumulator = metricsByMiniApp.get(projection.getMiniAppCode());
            if (accumulator != null) {
                accumulator.apply(projection);
            }
        });

        List<RealmMiniAppMetricsResponse> response = metricsByMiniApp.values().stream()
                .map(accumulator -> accumulator.toResponse(realmCode, normalizedWindowSeconds, nowEpochSecond))
                .sorted((left, right) -> Long.compare(right.totalRequests(), left.totalRequests()))
                .toList();

        cache.put(cacheKey, new CacheEntry(response, nowEpochSecond + CACHE_TTL_SECONDS));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public RealmMiniAppMetricsResponse getRealmMiniAppMetrics(String realmCode, String miniAppCode, Integer windowSeconds) {
        return getRealmMetrics(realmCode, windowSeconds).stream()
                .filter(item -> Objects.equals(item.miniAppCode(), miniAppCode))
                .findFirst()
                .orElseThrow(() -> new ServiceException("Realm miniapp not found", 404));
    }

    private void ensureRealmExists(String realmCode) {
        if (!realmRepository.existsByCode(realmCode)) {
            throw new ServiceException("Realm not found", 404);
        }
    }

    private int normalizeWindowSeconds(Integer windowSeconds) {
        if (windowSeconds == null) {
            return DEFAULT_WINDOW_SECONDS;
        }
        return Math.max(MIN_WINDOW_SECONDS, Math.min(MAX_WINDOW_SECONDS, windowSeconds));
    }

    private record CacheEntry(List<RealmMiniAppMetricsResponse> items, long expiresAtEpochSecond) {
    }

    private static final class RealmMiniAppMetricsAccumulator {
        private final String miniAppCode;
        private final String miniAppName;
        private final com.kemaless.core.enums.RealmMiniAppStatus currentStatus;
        private long launchesSuccess;
        private long launchesDenied;
        private long ssoSuccess;
        private long ssoFailed;

        private RealmMiniAppMetricsAccumulator(String miniAppCode, String miniAppName,
                                               com.kemaless.core.enums.RealmMiniAppStatus currentStatus) {
            this.miniAppCode = miniAppCode;
            this.miniAppName = miniAppName;
            this.currentStatus = currentStatus;
        }

        static RealmMiniAppMetricsAccumulator from(RealmMiniApp realmMiniApp) {
            return new RealmMiniAppMetricsAccumulator(
                    realmMiniApp.getMiniApp().getCode(),
                    realmMiniApp.getMiniApp().getName(),
                    realmMiniApp.getStatus()
            );
        }

        void apply(AuditMiniAppMetricsProjection projection) {
            AuditAction action = AuditAction.valueOf(projection.getAction());
            long value = projection.getEventsCount();
            switch (action) {
                case LAUNCH_SUCCESS -> launchesSuccess += value;
                case LAUNCH_DENIED -> launchesDenied += value;
                case SSO_EXCHANGE_SUCCESS -> ssoSuccess += value;
                case SSO_EXCHANGE_FAILED -> ssoFailed += value;
                default -> {
                }
            }
        }

        RealmMiniAppMetricsResponse toResponse(String realmCode, long windowSeconds, long cachedAtEpochSecond) {
            long totalRequests = launchesSuccess + launchesDenied + ssoSuccess + ssoFailed;
            return RealmMiniAppMetricsResponse.builder()
                    .realmCode(realmCode)
                    .miniAppCode(miniAppCode)
                    .miniAppName(miniAppName)
                    .currentStatus(currentStatus)
                    .launchesSuccess(launchesSuccess)
                    .launchesDenied(launchesDenied)
                    .ssoSuccess(ssoSuccess)
                    .ssoFailed(ssoFailed)
                    .totalRequests(totalRequests)
                    .lastWindowSeconds(windowSeconds)
                    .cachedAtEpochSecond(cachedAtEpochSecond)
                    .build();
        }
    }
}
