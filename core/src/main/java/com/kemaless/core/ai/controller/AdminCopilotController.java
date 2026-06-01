package com.kemaless.core.ai.controller;

import java.util.List;

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
    public AdminCopilotChatResponse chat(@Valid @RequestBody AdminCopilotChatRequest request) {
        requireCopilot();
        requireMediaForAttachments(request.attachments());
        String response = adminCopilotService.askCopilot(request.sessionId(), request.text(), request.attachments());
        return new AdminCopilotChatResponse(request.sessionId(), response);
    }

    @PostMapping("/media")
    public AdminCopilotMediaUploadResponse uploadMedia(
            @AuthenticationPrincipal AdminJwtPrincipal principal,
            @RequestParam("file") MultipartFile file
    ) {
        requireMediaUpload();
        return adminCopilotMediaService.upload(principal.userId(), file);
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
