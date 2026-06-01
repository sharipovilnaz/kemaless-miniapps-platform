package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.request.CreateRealmRoleRequest;
import com.kemaless.core.dto.request.UpdateRealmRoleRequest;
import com.kemaless.core.dto.response.RealmRoleResponse;

import java.util.List;

public interface RealmRoleService {
    RealmRoleResponse create(String realmCode, CreateRealmRoleRequest request);

    List<RealmRoleResponse> findAll(String realmCode);

    RealmRoleResponse update(String realmCode, String roleCode, UpdateRealmRoleRequest request);

    void delete(String realmCode, String roleCode);
}
