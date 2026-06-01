package com.kemaless.core.ai.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AiMinioConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        String endpoint = env.getProperty("platform.ai.media.minio.endpoint", "");
        String accessKey = env.getProperty("platform.ai.media.minio.access-key", "");
        String secretKey = env.getProperty("platform.ai.media.minio.secret-key", "");
        String bucket = env.getProperty("platform.ai.media.minio.bucket", "");
        return !endpoint.isBlank() && !accessKey.isBlank() && !secretKey.isBlank() && !bucket.isBlank();
    }
}
