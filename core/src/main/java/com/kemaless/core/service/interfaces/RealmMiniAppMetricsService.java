package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.response.RealmMiniAppMetricsResponse;

import java.util.List;

public interface RealmMiniAppMetricsService {

    List<RealmMiniAppMetricsResponse> getRealmMetrics(String realmCode, Integer windowSeconds);

    RealmMiniAppMetricsResponse getRealmMiniAppMetrics(String realmCode, String miniAppCode, Integer windowSeconds);
}
