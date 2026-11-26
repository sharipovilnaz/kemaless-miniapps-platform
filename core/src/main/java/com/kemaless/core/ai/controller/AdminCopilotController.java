package com.kemaless.core.ai.controller;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.kemaless.core.ai.config.AiAvailabilityRegistry;
import com.kemaless.core.ai.dto.api.AdminCopilotAttachmentRequest;
import com.kemaless.core.ai.dto.api.AdminCopilotChatRequest;
import com.kemaless.core.ai.dto.api.AdminCopilotChatResponse;
import com.kemaless.core.ai.dto.api.AdminCopilotMediaUploadResponse;
import com.kemaless.core.ai.exception.AiUnavailableException;
import com.kemaless.core.ai.exception.MediaStorageUnavailableException;
import com.kemaless.core.ai.service.AdminCopilotMediaService;
import com.kemaless.core.ai.service.AdminCopilotServiceImpl;
import com.kemaless.core.auth.dto.AdminJwtPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/copilot")
@PreAuthorize("@adminPermissionEvaluator.canUseCopilot(authentication)")
public class AdminCopilotController {

    private final AdminCopilotServiceImpl adminCopilotService;
    private final AdminCopilotMediaService adminCopilotMediaService;
    private final AiAvailabilityRegistry aiAvailabilityRegistry;

    public AdminCopilotController(
            AdminCopilotServiceImpl adminCopilotService,
            AdminCopilotMediaService adminCopilotMediaService,
            AiAvailabilityRegistry aiAvailabilityRegistry
    ) {
        this.adminCopilotService = adminCopilotService;
        this.adminCopilotMediaService = adminCopilotMediaService;
        this.aiAvailabilityRegistry = aiAvailabilityRegistry;
    }

    @PostMapping("/chat")
    public AdminCopilotChatResponse chat(
            @AuthenticationPrincipal AdminJwtPrincipal principal,
            @Valid @RequestBody AdminCopilotChatRequest request
    ) {
        AdminJwtPrincipal authenticated = requirePrincipal(principal);
        requireCopilot();
        requireMediaForAttachments(request.attachments());
        String response = adminCopilotService.askCopilot(
                authenticated, request.sessionId(), request.text(), request.attachments());
        return new AdminCopilotChatResponse(request.sessionId(), response);
    }

    @PostMapping("/media")
    public AdminCopilotMediaUploadResponse uploadMedia(
            @AuthenticationPrincipal AdminJwtPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        AdminJwtPrincipal authenticated = requirePrincipal(principal);
        requireMediaUpload();
        return adminCopilotMediaService.upload(authenticated, file);
    }

    private AdminJwtPrincipal requirePrincipal(AdminJwtPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new AccessDeniedException("Требуется аутентификация администратора");
        }
        return principal;
    }

    private void requireCopilot() {
        if (!aiAvailabilityRegistry.isCopilotAvailable()) {
            throw new AiUnavailableException(aiAvailabilityRegistry.getCopilotReason());
        }
    }

    private void requireMediaForAttachments(List<AdminCopilotAttachmentRequest> attachments) {
        if (attachments != null && !attachments.isEmpty() && !aiAvailabilityRegistry.isMediaAvailable()) {
            throw new MediaStorageUnavailableException(aiAvailabilityRegistry.getMediaReason());
        }
    }

    private void requireMediaUpload() {
        if (!aiAvailabilityRegistry.isMediaAvailable()) {
            throw new MediaStorageUnavailableException(aiAvailabilityRegistry.getMediaReason());
        }
    }
}
