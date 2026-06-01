package com.kemaless.core.ai.configuration;

import com.kemaless.core.ai.config.PlatformAiProperties;
import com.kemaless.core.ai.rag.MiniAppVectorStoreIndexer;
import com.kemaless.core.repository.MiniAppRepository;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "platform.ai", name = "rag.enabled", havingValue = "true")
@ConditionalOnBean(VectorStore.class)
public class AiRagConfiguration {

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(
        VectorStore vectorStore,
        PlatformAiProperties platformAiProperties
) {

        PlatformAiProperties.Rag rag = platformAiProperties.getRag();
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(rag.getTopK())
                .similarityThreshold(rag.getSimilarityThreshold())
                .build();

        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();
    }

    @Bean
    public MiniAppVectorStoreIndexer miniAppVectorStoreIndexer(
            MiniAppRepository miniAppRepository,
            VectorStore vectorStore,
            PlatformAiProperties platformAiProperties
    ) {
        return new MiniAppVectorStoreIndexer(
                miniAppRepository,
                vectorStore,
                platformAiProperties.getRag().isBootstrapMiniappsOnStartup()
        );
    }
}
