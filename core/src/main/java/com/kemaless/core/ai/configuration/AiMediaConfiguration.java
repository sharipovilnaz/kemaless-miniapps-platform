package com.kemaless.core.ai.configuration;

import com.kemaless.core.ai.config.AiMinioConfiguredCondition;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "platform.ai", name = "media.enabled", havingValue = "true")
@Conditional(AiMinioConfiguredCondition.class)
public class AiMediaConfiguration {

    @Bean
    public MinioClient minioClient(
            @Value("${platform.ai.media.minio.endpoint}") String endpoint,
            @Value("${platform.ai.media.minio.access-key}") String accessKey,
            @Value("${platform.ai.media.minio.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(
            MinioClient minioClient,
            @Value("${platform.ai.media.minio.bucket}") String bucket,
            @Value("${platform.ai.media.minio.bootstrap-bucket-on-startup:true}") boolean bootstrapBucketOnStartup
    ) {
        return args -> {
            if (!bootstrapBucketOnStartup) {
                return;
            }

            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
            }
        };
    }
}
