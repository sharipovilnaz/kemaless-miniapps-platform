package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.request.CreateRealmClientRequest;
import com.kemaless.core.dto.response.CreateRealmClientResponse;
import com.kemaless.core.dto.response.RealmClientResponse;
import com.kemaless.core.dto.response.RotateSecretResponse;

import java.util.List;

public interface RealmClientService {
    List<RealmClientResponse> findAllByRealm(String realmCode);
    CreateRealmClientResponse create(String realmCode, CreateRealmClientRequest request);
    RotateSecretResponse rotateSecret(String realmCode, String clientId);
    void changeStatus(String realmCode, String clientId, boolean active);
}
