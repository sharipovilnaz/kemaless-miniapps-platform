package com.kemaless.core.ai.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class PgVectorAutoConfigurationDisabler implements EnvironmentPostProcessor {

    private static final String EXCLUDE_KEY = "spring.autoconfigure.exclude";
    private static final String PGVECTOR_AUTO_CONFIG =
            "org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration";
    private static final String OPENAI_CHAT_AUTO_CONFIG =
            "org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkChatAutoConfiguration";
    private static final String OPENAI_EMBEDDING_AUTO_CONFIG =
            "org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkEmbeddingAutoConfiguration";
    private static final String OPENAI_IMAGE_AUTO_CONFIG =
            "org.springframework.ai.model.openaisdk.autoconfigure.OpenAiSdkImageAutoConfiguration";
    private static final String POSTGRESML_EMBEDDING_AUTO_CONFIG =
            "org.springframework.ai.model.postgresml.autoconfigure.PostgresMlEmbeddingAutoConfiguration";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String excludes = environment.getProperty(EXCLUDE_KEY, "");
        boolean ragEnabled = environment.getProperty("platform.ai.rag.enabled", Boolean.class, false);
        if (!ragEnabled) {
            excludes = addExclude(excludes, PGVECTOR_AUTO_CONFIG);
        }

        boolean copilotEnabled = environment.getProperty("platform.ai.copilot.enabled", Boolean.class, false);
        String openAiApiKey = environment.getProperty("spring.ai.openai-sdk.api-key", "");
        if (!copilotEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            excludes = addExclude(excludes, OPENAI_CHAT_AUTO_CONFIG);
        }
        if (!ragEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            excludes = addExclude(excludes, OPENAI_EMBEDDING_AUTO_CONFIG);
        }
        if (ragEnabled && openAiApiKey != null && !openAiApiKey.isBlank()) {
            excludes = addExclude(excludes, POSTGRESML_EMBEDDING_AUTO_CONFIG);
        }
        boolean mediaEnabled = environment.getProperty("platform.ai.media.enabled", Boolean.class, false);
        if (!mediaEnabled || openAiApiKey == null || openAiApiKey.isBlank()) {
            excludes = addExclude(excludes, OPENAI_IMAGE_AUTO_CONFIG);
        }

        Map<String, Object> map = new HashMap<>();
        map.put(EXCLUDE_KEY, excludes);
        environment.getPropertySources().addFirst(new MapPropertySource("kemaless-pgvector-disable", map));
    }

    private String addExclude(String existing, String candidate) {
        if (existing != null && existing.contains(candidate)) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return candidate;
        }
        return existing + "," + candidate;
    }
}
