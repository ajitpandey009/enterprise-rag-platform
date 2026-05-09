package com.enterprise.rag.config;

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j Configuration — Sets up the PgVector embedding store
 * and other LangChain4j-specific beans.
 *
 * The chat model, streaming model, and embedding model are auto-configured
 * by the LangChain4j Spring Boot starters based on application.yml properties.
 */
@Configuration
public class LangChain4jConfig {

    @Value("${app.pgvector.host}")
    private String pgHost;

    @Value("${app.pgvector.port}")
    private int pgPort;

    @Value("${app.pgvector.database}")
    private String pgDatabase;

    @Value("${app.pgvector.user}")
    private String pgUser;

    @Value("${app.pgvector.password}")
    private String pgPassword;

    @Value("${app.pgvector.table}")
    private String pgTable;

    @Value("${app.pgvector.dimension}")
    private int pgDimension;

    /**
     * PgVector Embedding Store — Stores and retrieves document embeddings
     * using PostgreSQL's pgvector extension for efficient similarity search.
     *
     * The HNSW index is created in the Flyway migration for optimal performance.
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(pgUser)
                .password(pgPassword)
                .table(pgTable)
                .dimension(pgDimension)
                .createTable(true)
                .useIndex(true)
                .build();
    }
}
