package dev.sivalabs.helpdesk.knowledgebase;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * An in-memory vector store, filled from the classpath on every start: the documents ship with the application, so
 * there is nothing to keep between runs.
 */
@Configuration
@EnableConfigurationProperties(KnowledgeBaseProperties.class)
class KnowledgeBaseConfig {

    @Bean
    VectorStore knowledgeBaseVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
