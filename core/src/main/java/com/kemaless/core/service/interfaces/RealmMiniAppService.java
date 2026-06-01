package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.request.AccessPolicyRequest;
import com.kemaless.core.dto.request.ConnectMiniAppRequest;
import com.kemaless.core.dto.request.UpdateRealmMiniAppRequest;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.RealmMiniAppListItemResponse;
import com.kemaless.core.dto.response.RealmMiniAppResponse;
import org.springframework.data.domain.Pageable;

public interface RealmMiniAppService {
    RealmMiniAppResponse connect(String realmCode, ConnectMiniAppRequest request);

    PageResponse<RealmMiniAppListItemResponse> findAll(String realmCode, String status, String search, Pageable pageable);

    RealmMiniAppResponse getByCode(String realmCode, String miniAppCode);

    RealmMiniAppResponse update(String realmCode, String miniAppCode, UpdateRealmMiniAppRequest request);

    void publish(String realmCode, String miniAppCode);

    void stop(String realmCode, String miniAppCode);

    void delete(String realmCode, String miniAppCode);

    RealmMiniAppResponse updateAccessPolicy(String realmCode, String miniAppCode, AccessPolicyRequest request);
}
