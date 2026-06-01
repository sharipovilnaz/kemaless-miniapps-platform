package com.kemaless.core.ai.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAvailabilityInitializer implements ApplicationListener<ContextRefreshedEvent>, Ordered {

    private final PlatformAiProperties platformAiProperties;
    private final AiAvailabilityRegistry registry;
    private final ApplicationContext applicationContext;

    private volatile boolean initialized;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != applicationContext) {
            return;
        }
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            initialized = true;
            evaluateCopilot();
            evaluateRag();
            evaluateMedia();
        }
    }

    private void evaluateCopilot() {
        if (!platformAiProperties.getCopilot().isEnabled()) {
            registry.setCopilot(false, "Copilot отключён (platform.ai.copilot.enabled=false).");
            return;
        }
        if (!applicationContext.getBeansOfType(org.springframework.ai.chat.client.ChatClient.class).isEmpty()) {
            registry.setCopilot(true, "Copilot доступен.");
            return;
        }
        registry.setCopilot(false,
                "Copilot включён в конфигурации, но нет bean ChatClient (проверьте ключ API, настройки spring.ai.openai-sdk и AI-конфигурацию).");
    }

    private void evaluateRag() {
        if (!platformAiProperties.getRag().isEnabled()) {
            registry.setRag(false, "RAG отключён (platform.ai.rag.enabled=false).");
            return;
        }
        if (applicationContext.getBeansOfType(org.springframework.ai.vectorstore.VectorStore.class).isEmpty()) {
            registry.setRag(false,
                    "RAG включён, но VectorStore недоступен (pgvector / Spring AI vector store).");
            return;
        }
        if (!platformAiProperties.getValidation().isCheckOnStartup()) {
            registry.setRag(true, "RAG разрешён конфигурацией (проверка pgvector при старте отключена).");
            return;
        }
        try {
            if (!isPgVectorReady()) {
                registry.setRag(false,
                        "RAG включён, но в БД нет расширения vector и/или таблицы vector_store.");
                return;
            }
            registry.setRag(true, "RAG доступен (pgvector).");
        } catch (Exception e) {
            log.warn("RAG: не удалось проверить pgvector: {}", e.getMessage());
            registry.setRag(false, "RAG: ошибка проверки БД: " + e.getMessage());
        }
    }

    private boolean isPgVectorReady() {
        try {
            DataSource dataSource = applicationContext.getBean(DataSource.class);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Integer ext = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                    Integer.class
            );
            if (ext == null || ext == 0) {
                return false;
            }
            Integer tbl = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vector_store'",
                    Integer.class
            );
            return tbl != null && tbl > 0;
        } catch (Exception e) {
            log.warn("RAG: проверка pgvector не применима или завершилась ошибкой: {}", e.getMessage());
            return false;
        }
    }

    private void evaluateMedia() {
        if (!platformAiProperties.getMedia().isEnabled()) {
            registry.setMedia(false, "Медиа-хранилище отключено (platform.ai.media.enabled=false).");
            return;
        }
        PlatformAiProperties.Media.Minio minio = platformAiProperties.getMedia().getMinio();
        if (!minio.isComplete()) {
            registry.setMedia(false,
                    "Медиа включено, но MinIO не сконфигурирован полностью (endpoint, access-key, secret-key, bucket).");
            return;
        }
        Optional<MinioClient> client = getOptionalBean(MinioClient.class);
        if (client.isEmpty()) {
            registry.setMedia(false,
                    "MinIO bean не создан (проверьте platform.ai.media.enabled и переменные MinIO).");
            return;
        }
        if (!platformAiProperties.getValidation().isCheckOnStartup()) {
            registry.setMedia(true, "Медиа разрешено конфигурацией (проверка MinIO при старте отключена).");
            return;
        }
        try {
            boolean exists = client.get().bucketExists(
                    BucketExistsArgs.builder().bucket(minio.getBucket()).build()
            );
            if (!exists && !minio.isBootstrapBucketOnStartup()) {
                registry.setMedia(false, "Bucket MinIO «" + minio.getBucket() + "» не существует.");
                return;
            }
            registry.setMedia(true, "MinIO доступен.");
        } catch (Exception e) {
            log.warn("Media: MinIO недоступен: {}", e.getMessage());
            registry.setMedia(false, "MinIO недоступен: " + e.getMessage());
        }
    }

    private <T> Optional<T> getOptionalBean(Class<T> type) {
        try {
            return Optional.of(applicationContext.getBean(type));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
