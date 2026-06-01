package com.kemaless.core.ai.rag;

import com.kemaless.core.model.MiniApp;
import com.kemaless.core.repository.MiniAppRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MiniAppVectorStoreIndexer implements ApplicationRunner {

    private final MiniAppRepository miniAppRepository;
    private final VectorStore vectorStore;
    private final boolean bootstrapMiniAppsOnStartup;

    public MiniAppVectorStoreIndexer(
            MiniAppRepository miniAppRepository,
            VectorStore vectorStore,
            boolean bootstrapMiniAppsOnStartup
    ) {
        this.miniAppRepository = miniAppRepository;
        this.vectorStore = vectorStore;
        this.bootstrapMiniAppsOnStartup = bootstrapMiniAppsOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapMiniAppsOnStartup) {
            return;
        }

        List<MiniApp> miniApps = miniAppRepository.findAll();
        if (miniApps.isEmpty()) {
            return;
        }

        List<String> documentIds = miniApps.stream()
                .map(this::documentId)
                .toList();

        vectorStore.delete(documentIds);

        List<Document> documents = miniApps.stream()
                .map(this::toDocument)
                .toList();

        vectorStore.add(documents);
    }

    private Document toDocument(MiniApp miniApp) {
        return Document.builder()
                .id(documentId(miniApp))
                .text(documentText(miniApp))
                .metadata("sourceType", "miniapp")
                .metadata("miniAppId", miniApp.getId().toString())
                .metadata("code", miniApp.getCode())
                .metadata("name", miniApp.getName())
                .metadata("status", miniApp.getStatus().name())
                .metadata("baseUrl", miniApp.getBaseUrl())
                .metadata("entryPath", miniApp.getEntryPath())
                .metadata("allowedRoles", miniApp.getAllowedRoles() == null ? List.of() : miniApp.getAllowedRoles())
                .metadata("themeColor", miniApp.getThemeColor())
                .metadata("updatedAt", miniApp.getUpdatedAt() == null ? null : miniApp.getUpdatedAt().toString())
                .metadata("metadata", miniApp.getMetadata() == null ? Map.of() : miniApp.getMetadata())
                .build();
    }

    private String documentId(MiniApp miniApp) {
        return "miniapp:" + miniApp.getId();
    }

    private String documentText(MiniApp miniApp) {
        String description = blankFallback(miniApp.getDescription(), "Описание не указано");
        String allowedRoles = miniApp.getAllowedRoles() == null || miniApp.getAllowedRoles().isEmpty()
                ? "не ограничены"
                : String.join(", ", miniApp.getAllowedRoles());

        return String.join("\n",
                "Миниапп: " + blankFallback(miniApp.getName(), miniApp.getCode()),
                "Код: " + blankFallback(miniApp.getCode(), "unknown"),
                "Статус: " + miniApp.getStatus().name(),
                "Описание: " + description,
                "Base URL: " + blankFallback(miniApp.getBaseUrl(), "не указан"),
                "Entry path: " + blankFallback(miniApp.getEntryPath(), "не указан"),
                "Разрешённые роли: " + allowedRoles,
                "Цвет темы: " + blankFallback(miniApp.getThemeColor(), "не указан"),
                "Иконка: " + blankFallback(miniApp.getIconUrl(), "не указана"),
                "Доп. metadata: " + Objects.toString(miniApp.getMetadata(), "{}")
        );
    }

    private String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
