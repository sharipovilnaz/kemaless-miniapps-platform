package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.request.CreateRealmRequest;
import com.kemaless.core.dto.request.UpdateRealmRequest;
import com.kemaless.core.dto.response.CreateRealmResponse;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.RealmDetailsResponse;
import com.kemaless.core.dto.response.RealmListItemResponse;
import org.springframework.data.domain.Pageable;

public interface RealmService {

    CreateRealmResponse create(CreateRealmRequest request);
    PageResponse<RealmListItemResponse> findAll(String search, String status, Pageable pageable);
    RealmDetailsResponse getByCode(String realmCode);
    void update(String realmCode, UpdateRealmRequest request);
    void changeStatus(String realmCode, boolean enable);
    void delete(String realmCode);
}
