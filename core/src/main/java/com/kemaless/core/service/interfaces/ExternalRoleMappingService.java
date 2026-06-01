package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.request.CreateRoleMappingRequest;
import com.kemaless.core.dto.request.UpdateRoleMappingRequest;
import com.kemaless.core.dto.response.ObservedRoleResponse;
import com.kemaless.core.dto.response.RoleMappingResponse;

import java.util.List;
import java.util.UUID;

public interface ExternalRoleMappingService {
    RoleMappingResponse createMapping(String realmCode, CreateRoleMappingRequest request);

    List<RoleMappingResponse> getMappings(String realmCode);

    RoleMappingResponse updateMapping(String realmCode, UUID mappingId, UpdateRoleMappingRequest request);

    void deleteMapping(String realmCode, UUID mappingId);

    List<ObservedRoleResponse> getObservedRoles(String realmCode);

    void ignoreObservedRole(String realmCode, UUID observedId);
}
