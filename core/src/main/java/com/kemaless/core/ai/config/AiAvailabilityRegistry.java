package com.kemaless.core.ai.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


@Component
public class AiAvailabilityRegistry {

    private final AtomicBoolean copilotAvailable = new AtomicBoolean(false);
    private final AtomicReference<String> copilotReason = new AtomicReference<>("not initialized");

    private final AtomicBoolean ragAvailable = new AtomicBoolean(false);
    private final AtomicReference<String> ragReason = new AtomicReference<>("not initialized");

    private final AtomicBoolean mediaAvailable = new AtomicBoolean(false);
    private final AtomicReference<String> mediaReason = new AtomicReference<>("not initialized");

    public void setCopilot(boolean available, String reason) {
        copilotAvailable.set(available);
        copilotReason.set(reason == null ? "" : reason);
    }

    public void setRag(boolean available, String reason) {
        ragAvailable.set(available);
        ragReason.set(reason == null ? "" : reason);
    }

    public void setMedia(boolean available, String reason) {
        mediaAvailable.set(available);
        mediaReason.set(reason == null ? "" : reason);
    }

    public boolean isCopilotAvailable() {
        return copilotAvailable.get();
    }

    public String getCopilotReason() {
        return copilotReason.get();
    }

    public boolean isRagAvailable() {
        return ragAvailable.get();
    }

    public String getRagReason() {
        return ragReason.get();
    }

    public boolean isMediaAvailable() {
        return mediaAvailable.get();
    }

    public String getMediaReason() {
        return mediaReason.get();
    }
}
