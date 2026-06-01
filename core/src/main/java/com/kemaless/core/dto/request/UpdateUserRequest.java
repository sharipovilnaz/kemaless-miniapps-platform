package com.kemaless.core.dto.request;

import com.kemaless.core.auth.enums.PlatformUserStatus;
import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
        @Email String email,
        PlatformUserStatus status
) {}
