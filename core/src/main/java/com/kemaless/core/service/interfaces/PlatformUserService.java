package com.kemaless.core.service.interfaces;

import com.kemaless.core.dto.request.ChangeAccessRequest;
import com.kemaless.core.dto.request.CreateUserRequest;
import com.kemaless.core.dto.request.UpdateUserRequest;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.PlatformUserResponse;
import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PlatformUserService {
    PlatformUserResponse create(CreateUserRequest request);
    PageResponse<PlatformUserResponse> findAll(String search, PlatformUserStatus status, GlobalRole role, String realmCode, Pageable pageable);
    PlatformUserResponse getById(UUID userId);
    PlatformUserResponse update(UUID userId, UpdateUserRequest request);
    PlatformUserResponse changeAccess(UUID userId, ChangeAccessRequest request);
}
