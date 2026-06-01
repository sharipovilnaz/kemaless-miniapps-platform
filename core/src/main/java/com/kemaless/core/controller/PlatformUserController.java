package com.kemaless.core.controller;

import com.kemaless.core.auth.enums.GlobalRole;
import com.kemaless.core.auth.enums.PlatformUserStatus;
import com.kemaless.core.dto.request.ChangeAccessRequest;
import com.kemaless.core.dto.request.CreateUserRequest;
import com.kemaless.core.dto.request.UpdateUserRequest;
import com.kemaless.core.dto.response.PageResponse;
import com.kemaless.core.dto.response.PlatformUserResponse;
import com.kemaless.core.service.interfaces.PlatformUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class PlatformUserController {

    private final PlatformUserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@adminPermissionEvaluator.canCreateUser(authentication, #body.realmCode())")
    public PlatformUserResponse createUser(@Valid @RequestBody CreateUserRequest body) {
        return userService.create(body);
    }

    @GetMapping
    @PreAuthorize("@adminPermissionEvaluator.canListUsers(authentication, #realmCode)")
    public PageResponse<PlatformUserResponse> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PlatformUserStatus status,
            @RequestParam(required = false) GlobalRole role,
            @RequestParam(required = false) String realmCode,
            Pageable pageable) {
        return userService.findAll(search, status, role, realmCode, pageable);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@adminPermissionEvaluator.canViewUser(authentication, #userId)")
    public PlatformUserResponse getUser(@PathVariable UUID userId) {
        return userService.getById(userId);
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("@adminPermissionEvaluator.canUpdateUser(authentication, #userId)")
    public PlatformUserResponse updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest body) {
        return userService.update(userId, body);
    }

    @PutMapping("/{userId}/access")
    @PreAuthorize("@adminPermissionEvaluator.canChangeUserAccess(authentication, #userId, #body.realmCode())")
    public PlatformUserResponse changeUserAccess(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangeAccessRequest body) {
        return userService.changeAccess(userId, body);
    }
}
