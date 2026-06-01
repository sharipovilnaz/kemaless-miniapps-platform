package com.kemaless.core.controller;

import com.kemaless.core.dto.response.RealmMiniAppMetricsResponse;
import com.kemaless.core.service.interfaces.RealmMiniAppMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/realms/{realmCode}/metrics/miniapps")
@RequiredArgsConstructor
public class RealmMiniAppMetricsController {

    private final RealmMiniAppMetricsService realmMiniAppMetricsService;

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public List<RealmMiniAppMetricsResponse> getRealmMiniAppMetrics(
            @PathVariable String realmCode,
            @RequestParam(required = false) Integer windowSeconds
    ) {
        return realmMiniAppMetricsService.getRealmMetrics(realmCode, windowSeconds);
    }

    @GetMapping("/{miniAppCode}")
    @PreAuthorize("@adminPermissionEvaluator.canViewRealm(authentication, #realmCode)")
    public RealmMiniAppMetricsResponse getRealmMiniAppMetricsByCode(
            @PathVariable String realmCode,
            @PathVariable String miniAppCode,
            @RequestParam(required = false) Integer windowSeconds
    ) {
        return realmMiniAppMetricsService.getRealmMiniAppMetrics(realmCode, miniAppCode, windowSeconds);
    }
}
